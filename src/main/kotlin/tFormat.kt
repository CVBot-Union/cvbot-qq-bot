import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val dateFormatIn = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy",Locale.UK)
val dateFormatOut = SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA)
val regex = "\n*https://t\\.co/\\w+\\s*\\Z".toRegex()

fun toUTC8(createdAt: String): String {
    val timeString = createdAt.replace("+0000 ", "")
    val timeStamp = dateFormatIn.parse(timeString).time + 28800*1000
    return dateFormatOut.format(Date(timeStamp))
}

private fun textFormat(tweet: JSONObject): String {
    val userName = if(tweet.has("initUserNickname")){
        tweet.getString("initUserNickname")
    }else{
        tweet.getJSONObject("user").getString("name")
    }

    val time = toUTC8(tweet.getString("created_at"))
    val parentTweet = when {
        tweet.has("quoted_status") -> textFormat(tweet.getJSONObject("quoted_status"))
        // tweet.has("retweeted_status") -> textFormat(tweet.getJSONObject("retweeted_status"))
        else -> ""
    }

    var text = if(tweet.getBoolean("truncated")){
        tweet.getJSONObject("extended_tweet").getString("full_text")
    }else{
        tweet.getString("text")
    }
    text = regex.replace(text, "")
    val type: String = when {
        !tweet.isNull("in_reply_to_status_id") -> "".also { text="回复: $text" }
        // tweet.has("retweeted_status") -> "\n转推:\n".also { text="" }
        tweet.has("quoted_status") -> "\n---转推---\n"
        else -> ""
    }
    return """#$userName#
        |$time
        |$text$type
        |$parentTweet""".trimMargin()
}

private fun transFormat(tweet: JSONObject): String {
    var text = if(tweet.getBoolean("truncated")){
        tweet.getJSONObject("extended_tweet").getString("full_text")
    }else{
        tweet.getString("text")
    }
    text = regex.replace(text, "")
    var quotedText = if(tweet.has("quoted_status")) {
        val quotedTweet = tweet.getJSONObject("quoted_status")
        if(quotedTweet.getBoolean("truncated")){
            quotedTweet.getJSONObject("extended_tweet").getString("full_text")
        }else {
            quotedTweet.getString("text")
        }
    } else ""
    quotedText = regex.replace(quotedText, "")
    val textToTranslate = if(quotedText!="") {
        "$text\n------\n$quotedText"
    } else { text }
    return translate(textToTranslate)
}

private fun mediaFormat(tweet: JSONObject): JSONObject {
    val formattedMedia = JSONObject()
    val mediaArray = when {
        tweet.has("extended_entities") -> tweet.getJSONObject("extended_entities").getJSONArray("media")
        tweet.getBoolean("truncated") && tweet.getJSONObject("extended_tweet").has("extended_entities")
        -> tweet.getJSONObject("extended_tweet").getJSONObject("extended_entities").getJSONArray("media")
        else -> JSONArray()
    }
    val parentMedia = if(tweet.has("quoted_status")) {
        mediaFormat(tweet.getJSONObject("quoted_status"))
    } else { JSONObject() }
    formattedMedia.put("photo", JSONArray().apply{
        if(!mediaArray.isEmpty) for(i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "photo") {
                this.put(media.getString("media_url"))
            }
        }
        if(!parentMedia.isEmpty) {
            val parentPhotos = parentMedia.getJSONArray("photo").toList()
            parentPhotos.forEach { this.put(it as String) }
        }
    })
    formattedMedia.put("video", JSONArray().apply {
        if(!mediaArray.isEmpty) for (i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "video") {
                this.put("https://cdn.cvbot.powerlayout.com/videos/${media.getString("id_str")}.mp4")
            }
        }
        if(!parentMedia.isEmpty) {
            val parentVideos = parentMedia.getJSONArray("video").toList()
            parentVideos.forEach { this.put(it as String) }
        }
    })
    return formattedMedia
}

fun tweetFormat(data: JSONObject): JSONObject {
    val tweet = data.getJSONObject("tweet")
    return JSONObject().apply {
        put("groups", tweet.getJSONArray("qqGroups"))
        put("text", textFormat(tweet))
        put("translation", transFormat(tweet))
        put("media_list", mediaFormat(tweet))
    }
}
