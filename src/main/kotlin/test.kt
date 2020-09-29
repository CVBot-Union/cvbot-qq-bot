import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

@ExperimentalCoroutinesApi
fun main() = runBlocking {
    try {
        val numbers = produceNumbers() // 从 1 开始生成整数
        square(numbers) // 整数求平方
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
    for (i in 1..100) {
        delay(500)
        println("已发送x=$x")
        send(x++)
    } // 从 1 开始的整数流
}

suspend fun square(numbers: ReceiveChannel<Int>) = coroutineScope {
    println("只执行一次")
    for (x in numbers) {
        launch { println("!") }
        println(x * x)
    }
}