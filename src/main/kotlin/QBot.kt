import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.serialization.json.JsonElement
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.closeAndJoin
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.sendAsImageTo
import net.mamoe.mirai.utils.*
import org.json.JSONArray
import org.json.JSONObject

import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun CoroutineScope.startServer() = produce {
    val server = LocalServer(1919, "UTF-8")
    // 开启一个新线程避免阻塞主线程
    launch(newSingleThreadContext("ServerThread")) {
        while (true) {
            val data: String = server.receiveData() // 阻塞直至收到数据
            send(data)
        }
    }
}

suspend fun bot(
        qqId: Long,
        dataChannel: ReceiveChannel<String>,
        defaultGroupId: Long=892887877L
) = coroutineScope {
    val file = File("./src/main/resources/deviceInfo.json")
    print("输入密码：")
    val password = Scanner(System.`in`).next()
    val bot = Bot(qqId,password) {
        if(file.exists()) {
            val fis = FileInputStream(file)
            val jsonString = String(fis.readAllBytes())
            fis.close()
            deviceInfo = { context ->
                json.decodeFromString(SystemDeviceInfo.serializer(), jsonString).also {it.context = context}
            }
        } else {
            deviceInfo = { context ->
                SystemDeviceInfo(context).also { info ->
                    launch {
                        val jsonEle: JsonElement = json.encodeToJsonElement(SystemDeviceInfo.serializer(), info)
                        file.writeText(jsonEle.toString())
                    }
                }
            }
        }
        protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD  //可以和手机同时在线，为默认值
        inheritCoroutineContext()
    }.alsoLogin()
    if(bot.isOnline) {
        bot.getGroup(defaultGroupId).sendMessage("bot已上线")
        launch {
            val imgFile = File("./src/main/resources/very_spirited.jpg")
            imgFile.sendAsImageTo(bot.getGroup(defaultGroupId))
        }
    }
    bot.subscribeAlways<GroupMessageEvent> {
        if(this.group.id == defaultGroupId && this.message.content == "#测试bot状态") {
            reply("在线")
            delay(500)
            launch { File("./src/main/resources/situation.jpg").run {
                this.sendAsImageTo(this@subscribeAlways.group)
            } }
        }
    }
    for(data in dataChannel) {
        val dataJson = JSONObject(data)
        if(dataJson["type"] == "tweet") {
            val tweet = tweetFormat(dataJson.getJSONObject("data"))
            val text: String = tweet.getString("text")
            //val uri: String = dataJson.getString("uri")
            val photoArray: JSONArray = tweet.getJSONArray("photo")
            val videoArray: JSONArray = tweet.getJSONArray("video")
            val groups = tweet.getJSONArray("groups").toList() as List<String>
            if (groups.isNotEmpty()) {
                if (text != "") launch {
                    //text += ("\n" + uri)
                    groups.forEach { bot.getGroup(it.toLong()).sendMessage(text) }
                }
                if(!photoArray.isEmpty) {
                    for(i in 0 until photoArray.length()) launch {
                        val inputStream = downloadImage(photoArray.getString(i))
                        groups.forEach { inputStream?.sendAsImageTo(bot.getGroup(it.toLong())) }
                    }
                }
                if(!videoArray.isEmpty) {
                    for(i in 0 until videoArray.length()) launch {
                        groups.forEach {
                            bot.getGroup(it.toLong()).sendMessage("视频下载地址：${videoArray.getString(i)}")
                        }
                    }
                }
            }
        } else {
            DefaultLogger(qqId.toString()).info(dataJson["data"].toString())
        }
    }
    withContext(NonCancellable) { bot.closeAndJoin() }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val qqId = 3174235713L
    try {
        val dataChannel = startServer()
        bot(qqId, dataChannel)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        coroutineContext.cancelChildren()
        DefaultLogger(qqId.toString()).info("bot已下线")
    }
}
