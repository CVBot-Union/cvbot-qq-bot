import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

@ExperimentalCoroutinesApi
fun main() = runBlocking {
    //println(toUTC8("Wed Jun 24 09:07:25 +0000 2020"))
    val inputStream = downloadImage("https://pbs.twimg.com/media/C_UdnvPUwAE3Dnn.jpg")
    val file = File("./src/main/resources/testImage.jpg")
    val fos = FileOutputStream(file)
    fos.write(inputStream?.readBytes())
    inputStream?.close()
    fos.close()
    //exitProcess(0)
    try {
        val numbers = produceNumbers() // 从 1 开始生成整数
        launch {square(numbers)}.join() // 整数求平方
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        println("Done!") // 至此已完成
        coroutineContext.cancelChildren() // 取消子协程
    }
}

@ExperimentalCoroutinesApi
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    for (i in 1..10) {
        delay(200)
        println("已发送x=$x")
        send(x++)
    } // 从 1 开始的整数流
}

suspend fun square(numbers: ReceiveChannel<Int>) = coroutineScope {
    println("只执行一次")
    for (x in numbers) {
        launch { println("!") }
        if(x==5) throw Exception("55")
        println(x * x)
    }
    withContext(NonCancellable) {
        println("善后处理")
    }
}