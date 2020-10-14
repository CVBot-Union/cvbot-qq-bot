import net.mamoe.mirai.utils.DefaultLogger
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URL
import javax.imageio.ImageIO

fun downloadImage(urlStr: String, timeOut: Int=10): BufferedImage? {
    try {
        //val addr = InetSocketAddress("127.0.0.1", 10809)
        //val proxy = Proxy(Proxy.Type.HTTP, addr) // http 代理
        val url = URL(urlStr)
        val httpConnect = url.openConnection() as HttpURLConnection
        httpConnect.connectTimeout = timeOut * 1000  // 设置连接超时时间
        httpConnect.requestMethod = "GET"
        httpConnect.setRequestProperty("Accept", "image/*")
        return if (httpConnect.responseCode == 200) {
            val bufferedImage = ImageIO.read(httpConnect.inputStream)
            httpConnect.inputStream.close()
            bufferedImage
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