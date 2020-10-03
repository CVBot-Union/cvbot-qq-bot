import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val dateFormatIn = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy",Locale.UK)
val dateFormatOut = SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA)

fun toUTC8(createdAt: String): String {
    val timeString = createdAt.replace("+0000 ", "")
    val timeStamp = dateFormatIn.parse(timeString).time + 28800*1000
    return dateFormatOut.format(Date(timeStamp))
}

private fun textFormat(tweet: JSONObject): String {
    val userName = if(tweet.has("initUserNickname")){
        tweet.getString("initUserNickname")
    }else{ tweet.getJSONObject("user").getString("name") }

    val time = toUTC8(tweet.getString("created_at"))
    val parentTweet = when {
        tweet.has("quoted_status") -> textFormat(tweet.getJSONObject("quoted_status"))
        tweet.has("retweeted_status") -> textFormat(tweet.getJSONObject("retweeted_status"))
        else -> ""
    }

    var text = if(tweet.getBoolean("truncated")){
        tweet.getJSONObject("extended_tweet").getString("full_text")
    }else{
        tweet.getString("text")
    }
    val type: String = when {
        tweet.get("in_reply_to_status_id")!=null -> "".also { text="回复$text" }
        tweet.has("retweeted_status") -> "\n转推:\n\n".also { text="" }
        tweet.has("quoted_status") -> "\n转推:\n\n"
        else -> ""
    }
    return """#$userName#
        |$time
        |$text$type
        |$parentTweet""".trimMargin()
}

private fun mediaFormat(tweet: JSONObject) : JSONObject {
    val formattedMedia = JSONObject()
    val mediaArray = tweet.getJSONObject("extended_entities").getJSONArray("media")
    formattedMedia.put("photo", JSONArray().apply{
        for(i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "photo") {
                this.put(media.getString("media_url"))
            }
        }
    })
    formattedMedia.put("video", JSONArray().apply {
        for (i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "video") {
                this.put("https://cdn.cvbot.powerlayout.com/videos/${media.getString("id_str")}.mp4")
            }
        }
    })
    return formattedMedia
}

fun tweetFormat(data: JSONObject): JSONObject {
    val tweet = data.getJSONObject("tweet")
    val formattedTweet = JSONObject().apply {
        put("groups", tweet.getJSONArray("qqGroups"))
        put("text", textFormat(tweet))
    }

    if(tweet.has("quoted_status")) {
        if(tweet.getJSONObject("quoted_status").has("extended_entities")) {
            formattedTweet.put("media_list", mediaFormat(tweet.getJSONObject("quoted_status")))
        }else if(tweet.getJSONObject("quoted_status").getBoolean("truncated")) {
            if(tweet.getJSONObject("quoted_status").getJSONObject("extended_tweet").has("extended_entities")) {
                formattedTweet.put("media_list", mediaFormat(tweet.getJSONObject("quoted_status").getJSONObject("extended_tweet")))
            }
        }
    } else if(tweet.has("retweeted_status")) {
        if(tweet.getJSONObject("retweeted_status").has("extended_entities")) {
            formattedTweet.put("media_list", mediaFormat(tweet.getJSONObject("retweeted_status")))
        }else if(tweet.getJSONObject("retweeted_status").getBoolean("truncated")) {
            if(tweet.getJSONObject("retweeted_status").getJSONObject("extended_tweet").has("extended_entities")) {
                formattedTweet.put("media_list", mediaFormat(tweet.getJSONObject("retweeted_status").getJSONObject("extended_tweet")))
            }
        }
    }

    return formattedTweet
}