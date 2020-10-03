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
    var parentTweet = ""
    if (tweet.has("quoted_status")) {
        parentTweet = textFormat(tweet.getJSONObject("quoted_status"))
    } else if (tweet.has("retweeted_status")) {
        parentTweet = textFormat(tweet.getJSONObject("retweeted_status"))
    }
    val type: String = when {
        tweet.has("in_reply_to_status_id") -> ""
        tweet.has("quoted_status") || tweet.has("retweeted_status") -> "\n\n转推\n\n"
        else -> ""
    }

    val text = if(tweet.getBoolean("truncated")){
        tweet.getJSONObject("extended_tweet").getString("full_text")
    }else{
        tweet.getString("text")
    }
    return """#$userName#
        |$time
        |$text$type
        |$parentTweet""".trimMargin()
}

private fun mediaFormat(tweet: JSONObject) : JSONObject {
    val formattedTweet = JSONObject()
    val mediaArray = tweet.getJSONObject("extended_entities").getJSONArray("media")
    formattedTweet.put("photo", JSONArray().apply{
        for(i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "photo") {
                this.put(media.getString("media_url"))
            }
        }
    })
    formattedTweet.put("video", JSONArray().apply {
        for (i in 0 until mediaArray.length()) {
            val media = mediaArray.getJSONObject(i)
            if (media.getString("type") == "video") {
                this.put("https://cdn.cvbot.powerlayout.com/videos/${media.getString("id_str")}.mp4")
            }
        }
    })
    return formattedTweet
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