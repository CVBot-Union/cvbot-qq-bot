import io.javalin.Javalin
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.getGroupOrNull
import net.mamoe.mirai.join
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.sendTo
import net.mamoe.mirai.message.sendAsImageTo
import net.mamoe.mirai.message.upload
import net.mamoe.mirai.utils.*
import org.json.JSONObject

import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.util.*

val NO_TRANSLATION = listOf("1076086233", "1095069733", "783768263", "932263215")

@ExperimentalCoroutinesApi
suspend fun bot(
    qqId: Long,
    httpServer: Javalin,
    defaultGroupId: Long=892887877L
) = coroutineScope {
    val file = File("deviceInfo.json")
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
    //val botQQId = bot.id
    //val botName = bot.nick
    val sampleGroup = bot.getGroup(defaultGroupId)
//    if(bot.isOnline) {
//        bot.getGroup(defaultGroupId).sendMessage("bot已上线")
//        launch {
//            val imgFile = File("very_spirited.jpg")
//            imgFile.sendAsImageTo(bot.getGroup(defaultGroupId))
//        }
//    }
    bot.subscribeAlways<GroupMessageEvent> {
        if(this.group.id == defaultGroupId && this.message.content == "#测试bot状态") {
            reply("在线")
            delay(200)
            launch { File("situation.jpg").run {
                this.sendAsImageTo(this@subscribeAlways.group)
            } }
        }
    }

    httpServer.post("/") { ctx ->
        val dataJson = JSONObject(ctx.body())
        ctx.result("OK")

        // 过滤所有单纯转推
        if(dataJson["type"] == "tweet" && !dataJson.getJSONObject("tweet").has("retweeted_status")) {
            val tweet = tweetFormat(dataJson.getJSONObject("data"))
            val text: String = tweet.getString("text")
            val translationArray = tweet.getJSONArray("translation")
            //val uri: String = dataJson.getString("uri")

            val photoArray = tweet.getJSONObject("media_list").getJSONArray("photo")
            val videoArray = tweet.getJSONObject("media_list").getJSONArray("video")

            val groups = tweet.getJSONArray("groups").toList() as List<String>
            if (groups.isNotEmpty()) launch {
                try {
                    //val messageCollection: MutableCollection<ForwardMessage.INode> = mutableListOf()
                    if (text != "") {
                        //text += ("\n" + uri)
                        //messageCollection.add(ForwardMessage.Node(botQQId, currentTimeSeconds.toInt(), botName, PlainText(text)))
                        groups.forEach { bot.getGroupOrNull(it.toLong())?.sendMessage(text) }
                    }
                    if (!translationArray.isEmpty) {
                        for (i in 0 until translationArray.length()) launch {
                            groups.forEach {
                                if(it !in NO_TRANSLATION)
                                    bot.getGroupOrNull(it.toLong())?.sendMessage(translationArray.getString(i))
                            }
                        }
                    }
                    if (!photoArray.isEmpty) {
                        for (i in 0 until photoArray.length()) launch {
                            val bufferedImage = async { downloadImage(photoArray.getString(i)) }
                            val imageToSend = bufferedImage.await()?.upload(sampleGroup)
                            groups.forEach {
                                val groupToSend = bot.getGroupOrNull(it.toLong())
                                if (groupToSend != null) imageToSend?.sendTo(groupToSend)
                            }
                            // 防止BufferedImage导致内存泄漏
                            bufferedImage.getCompleted()?.apply {
                                graphics?.dispose()
                                flush()
                            }
                        }
                    }
                    if (!videoArray.isEmpty) {
                        for (i in 0 until videoArray.length()) launch {
                            groups.forEach {
                                bot.getGroupOrNull(it.toLong())?.sendMessage("视频下载地址：${videoArray.getString(i)}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else if(dataJson["type"] != "tweet") {
            DefaultLogger(qqId.toString()).info(dataJson["data"].toString())
        }
    }

    withContext(NonCancellable) { bot.join() }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val qqId = 3174235713L
    try {
        val httpServer = Javalin.create().start(1919)
        bot(qqId, httpServer)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        coroutineContext.cancelChildren()
        DefaultLogger(qqId.toString()).info("bot已下线")
    }
}
