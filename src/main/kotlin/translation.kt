import net.mamoe.mirai.utils.DefaultLogger
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

const val urlStr = "http://api.fanyi.baidu.com/api/trans/vip/translate"
const val appId = "20201004000580074"
private const val secret = "42rPcdFfkNiYlIrke9XZ"

fun String.md5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
    return bytes.hex().toLowerCase()
}

fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }

private fun generateSign(text: String, salt: String): String = (appId+text+salt+secret).md5()

fun translate(text: String): String {
    if(text=="") return ""
    var translatedText = ""
    var outputStreamWriter: OutputStreamWriter? = null
    var inputStreamReader: InputStreamReader? = null
    try {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val salt = System.currentTimeMillis().toString()
        val sign = generateSign(text, salt)
        val data = "q=$encodedText&from=auto&to=zh&appid=$appId&salt=$salt&sign=$sign"
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
                if(resJson.get("error_code").toString()=="54003") {
                    Thread.sleep(900)
                    translatedText = translate(text)
                }
                DefaultLogger("translation").warning(resJson.get("error_code").toString())
            } else {
                translatedText = "（百度机翻参考）"
                val result = resJson.getJSONArray("trans_result")
                for(i in 0 until result.length()) {
                    translatedText += ("\n"+result.getJSONObject(i).getString("dst"))
                }
            }
        } else {
            DefaultLogger("translation").warning(conn.responseCode.toString())
            DefaultLogger("translation").warning(conn.responseMessage)
            conn.disconnect()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        outputStreamWriter?.close()
        inputStreamReader?.close()
        return translatedText
    }
}