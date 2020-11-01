import net.mamoe.mirai.utils.DefaultLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun downloadImage(urlStr: String, timeOut: Int=10): File? {
    try {
        //val addr = InetSocketAddress("127.0.0.1", 10809)
        //val proxy = Proxy(Proxy.Type.HTTP, addr) // http 代理
        val url = URL(urlStr)
        val httpConnect = url.openConnection() as HttpURLConnection
        httpConnect.connectTimeout = timeOut * 1000  // 设置连接超时时间
        httpConnect.requestMethod = "GET"
        httpConnect.setRequestProperty("Accept", "image/*")
        return if (httpConnect.responseCode == 200) {
            val file = createTempFile("qbot_tmp")
            Files.copy(httpConnect.inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            httpConnect.inputStream.close()
            file
        } else {
            DefaultLogger("downloadImage").warning(httpConnect.responseMessage)
            httpConnect.inputStream.close()
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}