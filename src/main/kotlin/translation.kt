import net.mamoe.mirai.utils.MiraiLogger
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

const val urlStr = "http://api.fanyi.baidu.com/api/trans/vip/translate"
val appIds = arrayOf("20201004000580074", "20201013000588582")
private val secrets = arrayOf("42rPcdFfkNiYlIrke9XZ", "UTWAgjaRf8zf_RRdF72N")

private var count = -1

val transLogger = MiraiLogger.create("translation")

fun String.md5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
    return bytes.hex().toLowerCase()
}

fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }

private fun generateSign(
        text: String,
        salt: String,
        appId: String,
        secret: String
): String = (appId+text+salt+secret).md5()

fun translate(text: String): String {
    if(text=="") return ""
    var translatedText = ""
    var outputStreamWriter: OutputStreamWriter? = null
    var inputStreamReader: InputStreamReader? = null
    count++
    val chosenAppId = appIds[count%2]
    val chosenSecret = secrets[count%2]
    try {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val salt = System.currentTimeMillis().toString()
        val sign = generateSign(text, salt, chosenAppId, chosenSecret)
        val data = "q=$encodedText&from=auto&to=zh&appid=$chosenAppId&salt=$salt&sign=$sign"
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Content-Length", data.length.toString())
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Connection", "Keep-Alive")
        outputStreamWriter = OutputStreamWriter(conn.outputStream, "UTF-8")
        outputStreamWriter.write(data)
        outputStreamWriter.flush()
        if(conn.responseCode==200) {
            inputStreamReader = InputStreamReader(conn.inputStream, "UTF-8")
            val resJson = JSONObject(inputStreamReader.readText())
            if(resJson.has("error_code")) {
                transLogger.warning(resJson.toString())
                if(resJson.get("error_code").toString()=="54003") {
                    translatedText = translate(text)
                }
            } else {
                translatedText = " [百度机翻参考] "
                val result = resJson.getJSONArray("trans_result")
                for(i in 0 until result.length()) {
                    translatedText += ("\n"+result.getJSONObject(i).getString("dst"))
                }
            }
        } else {
            transLogger.warning(conn.responseMessage)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        outputStreamWriter?.close()
        inputStreamReader?.close()
    }
    return translatedText
}