import net.mamoe.mirai.utils.DefaultLogger
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

fun downloadImage(urlStr: String, timeOut: Int=10): InputStream? {
    try {
        //val addr = InetSocketAddress("127.0.0.1", 10809)
        //val proxy = Proxy(Proxy.Type.HTTP, addr) // http 代理
        val url = URL(urlStr)
        val httpConnect = url.openConnection() as HttpURLConnection
        httpConnect.connectTimeout = timeOut * 1000  // 设置连接超时时间
        httpConnect.requestMethod = "GET"
        return if (httpConnect.responseCode == 200) {
            httpConnect.inputStream
        } else {
            DefaultLogger("downloadImage").warning(httpConnect.responseMessage)
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}