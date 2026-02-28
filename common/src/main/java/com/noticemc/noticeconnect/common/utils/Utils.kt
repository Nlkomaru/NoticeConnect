package com.noticemc.noticeconnect.common.utils

import com.noticemc.noticeconnect.common.discord.DiscordWebhookData
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object Utils {

    fun sendAudienceMessage(message: Component) {
        val mode = CustomConfig.config.mode
        NoticeConnectPlatform.bridge.broadcastMessage(message, mode)
    }

    val mm = MiniMessage.miniMessage()

    fun Component.toLegacyText(): String {
        return LegacyComponentSerializer.legacySection().serialize(this)
    }

    fun Component.toGsonText(): String {
        return GsonComponentSerializer.gson().serialize(this)
    }

    fun DiscordWebhookData.sendWebHook() {
        val json = Json.encodeToString(this)
        val url = CustomConfig.config.discord.url
        if (url.isBlank()) {
            return
        }

        try {
            val webHookUrl = URL(url)
            val con: HttpsURLConnection = (webHookUrl.openConnection() as HttpsURLConnection)

            con.addRequestProperty("Content-Type", "application/JSON; charset=utf-8")
            con.addRequestProperty("User-Agent", "DiscordBot")
            con.doOutput = true
            con.requestMethod = "POST"

            con.setRequestProperty("Content-Length", json.length.toString())

            val stream: OutputStream = con.outputStream
            stream.write(json.toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.close()

            val status: Int = con.responseCode
            if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_NO_CONTENT) {
                NoticeConnectPlatform.bridge.logger.warn("error:$status")
            }
            con.disconnect()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
