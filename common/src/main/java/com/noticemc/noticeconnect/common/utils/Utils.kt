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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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

    /** プラグイン全体で共有する HTTP クライアント (Discord の Webhook と mc-api)。 */
    val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /**
     * Discord の Webhook に送る。非同期で、呼び出し元のスレッドは塞がない。
     */
    fun DiscordWebhookData.sendWebHook() {
        val url = CustomConfig.config.discord.url
        if (url.isBlank()) {
            return
        }
        val json = Json.encodeToString(this)

        val request = try {
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "DiscordBot")
                .POST(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
                .build()
        } catch (e: IllegalArgumentException) {
            NoticeConnectPlatform.bridge.logger.warn("Invalid discord.url")
            return
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .whenComplete { response, error ->
                val logger = NoticeConnectPlatform.bridge.logger
                when {
                    error != null -> logger.warn("Failed to send the Discord webhook: ${error.message}")
                    response.statusCode() !in 200..299 -> logger.warn("error:${response.statusCode()}")
                }
            }
    }
}
