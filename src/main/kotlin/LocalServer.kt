import java.io.ByteArrayOutputStream
import java.net.ServerSocket

class LocalServer(mPort: Int=1919, private var charSet: String="UTF-8") {
    private val serverSocket = ServerSocket(mPort)

    fun changeCharSet(charSet: String) { this.charSet = charSet }

    fun receiveData(): String {
        val socket = serverSocket.accept()
        val iStream = socket.getInputStream()
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (iStream.read(buffer).also { length = it } != -1) {
            result.write(buffer, 0, length)
        }
        return result.toString(charSet)
    }
}