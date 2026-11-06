package com.noticemc.noticeconnect.common

import com.noticemc.noticeconnect.common.discord.Author
import com.noticemc.noticeconnect.common.discord.DiscordWebhookData
import com.noticemc.noticeconnect.common.discord.DiscordWebhookEmbed
import com.noticemc.noticeconnect.common.files.Config
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.files.Database
import com.noticemc.noticeconnect.common.files.Discord
import com.noticemc.noticeconnect.common.files.DiscordMessage
import com.noticemc.noticeconnect.common.files.McApi
import com.noticemc.noticeconnect.common.files.Message
import com.noticemc.noticeconnect.common.mcapi.McApiClient
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.common.platform.PlatformBridge
import com.noticemc.noticeconnect.common.utils.Utils.sendWebHook
import com.sun.net.httpserver.HttpServer
import net.kyori.adventure.text.Component
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 参加の Webhook が mc-api への登録を待ってから、積んだ順に送られることのテスト。
 * Discord と mc-api の代わりにローカルの HTTP サーバーを立てる。
 */
class WebhookOrderTest {
    private val uuid = UUID.fromString("00068061-a5fc-4e10-abb3-43e23006cf40")

    /** サーバーに届いたリクエストのパス */
    private val received = LinkedBlockingQueue<String>()

    /** mc-api の PUT の応答を止めておくラッチ */
    private val releasePut = CountDownLatch(1)

    private lateinit var server: HttpServer

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            exchange.requestBody.readAllBytes()
            received.put(path)
            if (exchange.requestMethod == "PUT") releasePut.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        val base = "http://127.0.0.1:${server.address.port}"
        CustomConfig.config = Config(
            version = "2.0.0",
            message = Message(listOf("x"), listOf("y"), listOf("z")),
            database = Database("NoticeConnect"),
            replace = emptyMap(),
            discord = Discord("$base/webhook", DiscordMessage("a", "b", "c")),
            mode = "Chat",
            mcApi = McApi(enabled = true, url = base, token = "token"),
        )
        NoticeConnectPlatform.bridge = object : PlatformBridge {
            override val dataDirectory: Path = Path.of(".")
            override val logger = LoggerFactory.getLogger("test")
            override fun broadcastMessage(message: Component, mode: String) = Unit
        }
    }

    @AfterTest
    fun tearDown() {
        releasePut.countDown()
        server.stop(0)
        (server.executor as java.util.concurrent.ExecutorService).shutdownNow()
    }

    private fun webhook(name: String) =
        DiscordWebhookData(arrayListOf(DiscordWebhookEmbed(0, Author(name, null, "https://example.com/a.png"))))

    @Test
    fun joinWebhookWaitsForRegistration() {
        val registered = McApiClient.assign(uuid, "Nikomaru")
        webhook("join").sendWebHook(after = registered)
        assertEquals("/v1/players/$uuid", received.poll(5, TimeUnit.SECONDS))
        // PUT の応答を返すまで、Webhook は届かない
        assertNull(received.poll(500, TimeUnit.MILLISECONDS))

        releasePut.countDown()
        assertEquals("/webhook", received.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun laterWebhookKeepsOrderBehindWaitingOne() {
        val registered = CompletableFuture<Boolean>()
        webhook("join").sendWebHook(after = registered)
        webhook("leave").sendWebHook()
        // 待っている参加の通知を、退出の通知が追い越さない
        assertNull(received.poll(500, TimeUnit.MILLISECONDS))

        registered.complete(true)
        assertEquals("/webhook", received.poll(5, TimeUnit.SECONDS))
        assertEquals("/webhook", received.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun registrationWaitIsCapped() {
        // mc-api が応答しなくても、参加の通知は上限 (5 秒) で送れるようになる
        val registered = McApiClient.assign(uuid, "Nikomaru")
        assertEquals(false, registered.get(7, TimeUnit.SECONDS))
    }
}
