import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val downloadLogger = MiraiLogger.create("downloadImage")

fun downloadImage(urlStr: String, timeOut: Int=10, retry: Boolean=true): File? {
    try {
        //val addr = InetSocketAddress("127.0.0.1", 10809)
        //val proxy = Proxy(Proxy.Type.HTTP, addr) // http 代理
        val url = URL(urlStr)
        val httpConnect = url.openConnection() as HttpURLConnection
        httpConnect.connectTimeout = timeOut * 1000  // 设置连接超时时间
        httpConnect.requestMethod = "GET"
        httpConnect.setRequestProperty("Accept", "image/*")
        return if (httpConnect.responseCode == 200) {
            val file = File.createTempFile("qbot_tmp", null)
            Files.copy(httpConnect.inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            httpConnect.inputStream.close()
            file
        } else {
            downloadLogger.warning(httpConnect.responseMessage)
            httpConnect.inputStream.close()
            if(retry) {
                downloadImage(urlStr, 10, false)
            } else null
        }
    } catch (e: Exception) {
        downloadLogger.warning(e.toString())
        return if(retry) {
            downloadImage(urlStr, 10, false)
        } else null
    }
}