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
    val userName = tweet.getJSONObject("user").getString("name")
    val time = toUTC8(tweet.getString("created_at"))
    var parentTweet = ""
    if(tweet.has("quoted_status")) {
        parentTweet = textFormat(tweet.getJSONObject("quoted_status"))
    } else if(tweet.has("retweeted_status")) {
        parentTweet = textFormat(tweet.getJSONObject("retweeted_status"))
    }
    val type: String = when {
        tweet.has("in_reply_to_status_id") -> ""
        tweet.has("quoted_status") || tweet.has("retweeted_status") -> "\n\n转推\n\n"
        else -> ""
    }
    val text = tweet.getString("text")
    return """
        #$userName#
        $time
        $text$type
        $parentTweet""".trimIndent()
}

fun tweetFormat(data: JSONObject): JSONObject {
    val tweet = data.getJSONObject("tweet")
    val formattedTweet = JSONObject().apply {
        put("groups", tweet.getJSONArray("qqGroups"))
        put("text", textFormat(tweet))
    }
    if(tweet.has("extended_entities")) {
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
    }
    return formattedTweet
}