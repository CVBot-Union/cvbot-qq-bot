import io.javalin.Javalin
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.getGroupOrNull
import net.mamoe.mirai.message.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList

val NO_TRANSLATION = listOf("1076086233", "1095069733", "783768263", "932263215", "252047639", "783541028")

@ExperimentalCoroutinesApi
suspend fun launchBot(
    qqId: Long,
    password: String,
    httpServer: Javalin,
    defaultGroupId: Long=892887877L
): Unit = coroutineScope {
    val file = File("deviceInfo.json")
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
                        val jsonEle = json.encodeToJsonElement(SystemDeviceInfo.serializer(), info)
                        file.writeText(jsonEle.toString())
                    }
                }
            }
        }
        protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD  //可以和手机同时在线，为默认值
        inheritCoroutineContext()
    }.alsoLogin()
    bot.logger.follower = SingleFileLogger(qqId.toString())
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
        val dataJson: JSONObject? = try {
            JSONObject(ctx.body())
        } catch (_: JSONException) {
            bot.logger.info("httpServer: "+ctx.body())
            null
        }
        ctx.result("OK")

        // 过滤所有单纯转推
        if (dataJson?.getString("type") == "tweet" && !dataJson.getJSONObject("data").getJSONObject("tweet").has("retweeted_status")) {
            bot.logger.info("got tweet "+dataJson.getJSONObject("data").getJSONObject("tweet").getString("id_str"))
            val tweet = tweetFormat(dataJson.getJSONObject("data"))
            val text: String = tweet.getString("text")
            val translation = tweet.getString("translation")
            //val uri: String = dataJson.getString("uri")

            val photoArray = tweet.getJSONObject("media_list").getJSONArray("photo")
            val videoArray = tweet.getJSONObject("media_list").getJSONArray("video")

            val groups = tweet.getJSONArray("groups").toList() as List<String>
            if (groups.isNotEmpty()) launch {
                val fileList: ArrayList<Deferred<File?>> = ArrayList()
                val messageList: MutableList<SingleMessage> = mutableListOf()
                val noTranslationMessageList: MutableList<SingleMessage> = mutableListOf()
                if (text != "") {
                    //text += ("\n" + uri)
                    messageList.add(PlainText(text))
                }
                if (translation != "") {
                    messageList.add(PlainText("\n\n$translation"))
                }

                if (!photoArray.isEmpty) {
                    for (i in 0 until photoArray.length()) {
                        val imageFile = async { downloadImage(photoArray.getString(i)) }
                        fileList.add(imageFile)
                    }
                    groups.forEach {
                        val thisGroup = bot.getGroupOrNull(it.toLong())
                        if(thisGroup != null) {
                            for (i in 0 until photoArray.length()) {
                                var imageToSend: Image? = null
                                var retry = 0
                                while (imageToSend == null && retry < 3) {
                                    imageToSend = fileList[i].await()?.uploadAsImage(thisGroup)
                                    retry += 1
                                }
                                if (imageToSend != null) {
                                    messageList.add(imageToSend)
                                } else {
                                    bot.logger.error("image upload to group $it failed")
                                }
                            }
                        }
                    }
                }

                if (!videoArray.isEmpty) {
                    for (i in 0 until videoArray.length()) {
                        messageList.add(PlainText("\n\n视频下载地址：${videoArray.getString(i)}"))
                    }
                }
                if (NO_TRANSLATION.isNotEmpty()) {
                    noTranslationMessageList.addAll(messageList)
                    if (noTranslationMessageList[1].contentEquals("\n\n$translation")) {
                        noTranslationMessageList.removeAt(1)
                    }
                }
                val messageChain = messageList.asMessageChain()
                val noTranslationMessageChain = if (noTranslationMessageList.isNotEmpty()) {
                    noTranslationMessageList.asMessageChain()
                } else {
                    null
                }
                groups.forEach {
                    launch {
                        try {
                            val thisGroup = bot.getGroup(it.toLong())
                            if (noTranslationMessageChain != null && (it in NO_TRANSLATION)) {
                                thisGroup.sendMessage(noTranslationMessageChain)
                            } else {
                                thisGroup.sendMessage(messageChain)
                            }
                        } catch(e: Exception) {
                            bot.logger.error(e.toString())
                        }
                    }
                }
            } else {
                bot.logger.info("no groups to send: "+dataJson.getJSONObject("data").getJSONObject("tweet").getString("id_str"))
            }
        } else if(dataJson != null) {
            bot.logger.info("ignored tweet "+dataJson.getJSONObject("data").getJSONObject("tweet").getString("id_str"))
        }
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val qqId = 3174235713L
    print("输入QQ${qqId}的密码：")
    val password = Scanner(System.`in`).next()
    while(true) {
        val httpServer = Javalin.create().start(1919)
        try {
            launchBot(qqId, password, httpServer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        httpServer.stop()
        coroutineContext.cancelChildren()
        DefaultLogger(qqId.toString()).info("bot已下线")
        System.gc()
    }
}
