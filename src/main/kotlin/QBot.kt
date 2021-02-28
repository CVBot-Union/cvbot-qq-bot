import io.javalin.Javalin
import kotlinx.coroutines.*
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.*

val NO_TRANSLATION = listOf("1076086233", "1095069733", "783768263", "932263215", "252047639", "783541028")

suspend fun launchBot(
    qqId: Long,
    password: String,
    httpServer: Javalin,
    defaultGroupId: Long=892887877L
): Unit = coroutineScope {
    val bot = BotFactory.newBot(qqId,password) {
        fileBasedDeviceInfo("deviceInfo.json")
        protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD  //可以和手机同时在线
    }.alsoLogin()
    bot.logger.follower = SingleFileLogger(qqId.toString())
//    if(bot.isOnline) {
//        bot.getGroup(defaultGroupId).sendMessage("bot已上线")
//        launch {
//            val imgFile = File("very_spirited.jpg")
//            imgFile.sendAsImageTo(bot.getGroup(defaultGroupId))
//        }
//    }
    bot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if(subject.id == defaultGroupId && this.message.content == "#测试bot状态") {
            subject.sendMessage("在线")
            delay(200)
            launch { subject.sendImage(File("situation.jpg")) }
        }
    }

    httpServer.get("/114514") { ctx->
        ctx.result("1919810")
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
                val deferredFileList: ArrayList<Deferred<File?>> = ArrayList()
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
                        deferredFileList.add(imageFile)
                    }
                    for (i in 0 until photoArray.length()) {
                        var photo: Image? = null
                        groups.forEach {
                            val thisGroup = bot.getGroup(it.toLong())
                            if(thisGroup != null) {
                                var retry = 0
                                var imageToSend: Image? = null
                                while (imageToSend == null && retry < 3) {
                                    imageToSend = deferredFileList[i].await()?.uploadAsImage(thisGroup)
                                    retry += 1
                                }
                                if(imageToSend==null) {
                                    bot.logger.error("image upload to group $it failed")
                                } else {
                                    photo = imageToSend
                                }
                            }
                        }
                        if (photo != null) {
                            messageList.add(photo!!)
                        } else {
                            bot.logger.error("image upload failed on tweet "+dataJson.getJSONObject("data").getJSONObject("tweet").getString("id_str"))
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
                val messageChain = messageList.toMessageChain()
                val noTranslationMessageChain = if (noTranslationMessageList.isNotEmpty()) {
                    noTranslationMessageList.toMessageChain()
                } else {
                    null
                }
                groups.forEach {
                    launch {
                        try {
                            val thisGroup = bot.getGroupOrFail(it.toLong())
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
        System.gc()
    }
}

suspend fun main(){
    val qqId = 3174235713L
    print("输入QQ${qqId}的密码：")
    val password = Scanner(System.`in`).next()
    val httpServer = Javalin.create().start(1919)
    try {
        launchBot(qqId, password, httpServer)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
