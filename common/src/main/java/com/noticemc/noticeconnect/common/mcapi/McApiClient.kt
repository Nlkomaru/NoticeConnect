package com.noticemc.noticeconnect.common.mcapi

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.database.DatabaseExecutor
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.files.McApi
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.common.utils.Utils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class AssignRequest(val uuid: String, val name: String)

@Serializable
data class BulkAssignRequest(val uuids: List<String>, val fetch: Boolean)

/**
 * mc-api (`<mcApi.url>/api/assign`) へプレイヤーを登録するクライアント。
 *
 * すべて非同期で、イベントスレッドを塞がない。トークンはログに出さない。
 */
object McApiClient {
    private const val BATCH_SIZE = 100
    private const val BATCH_INTERVAL_MILLIS = 1_000L
    private val TIMEOUT: Duration = Duration.ofSeconds(10)

    /** 失敗の警告を一度だけ出すためのフラグ。成功したら戻す (次の障害でまた警告する)。 */
    private val failureWarned = AtomicBoolean(false)

    /** 一括同期が走っているか (多重起動を防ぐ)。 */
    private val backfillRunning = AtomicBoolean(false)

    private val json = Json { encodeDefaults = true }

    /** 設定が有効で、URL とトークンがそろっているときだけ設定を返す。 */
    private fun activeConfig(): McApi? {
        val config = CustomConfig.config.mcApi
        if (!config.enabled || config.url.isBlank() || config.token.isBlank()) return null
        return config
    }

    fun isEnabled(): Boolean = activeConfig() != null

    /**
     * 参加したプレイヤーを mc-api に登録し、成功したら LAST_SYNCED_AT を更新する。
     */
    fun assign(uuid: UUID, name: String) {
        val config = activeConfig() ?: return
        val body = json.encodeToString(AssignRequest.serializer(), AssignRequest(uuid.toString(), name))
        post(config, body).thenAccept { ok ->
            if (ok) {
                DatabaseExecutor.execute { runCatching { Database.markSynced(listOf(uuid.toString())) } }
            }
        }
    }

    /**
     * 一度も同期していない行 (`LAST_SYNCED_AT IS NULL`) を 100 件ずつ、1 秒に 1 バッチの間隔で登録する。
     * 失敗したらそこで止める (次回の起動か `/noticeconnect sync` で続きから再開する)。
     *
     * @return 開始したら true、無効または実行中なら false
     */
    fun startBackfill(): Boolean {
        val config = activeConfig() ?: return false
        if (!backfillRunning.compareAndSet(false, true)) return false
        val logger = NoticeConnectPlatform.bridge.logger
        Thread({
            var cursor = ""
            var synced = 0
            try {
                while (true) {
                    val rows = DatabaseExecutor.submit { Database.unsyncedUuids(cursor, BATCH_SIZE) }.get()
                    if (rows.isEmpty()) break
                    cursor = rows.last()
                    // 旧データには "<uuid>-<数字>" のような壊れた値があるので、正しい形式だけ送る
                    val uuids = rows.filter(::isCanonicalUuid)
                    if (uuids.isNotEmpty()) {
                        val body = json.encodeToString(BulkAssignRequest.serializer(), BulkAssignRequest(uuids, false))
                        if (!post(config, body).get()) {
                            logger.warn("mc-api backfill stopped after $synced player(s); it will resume on the next run")
                            return@Thread
                        }
                        DatabaseExecutor.submit { Database.markSynced(uuids) }.get()
                        synced += uuids.size
                    }
                    Thread.sleep(BATCH_INTERVAL_MILLIS)
                }
                if (synced > 0) logger.info("mc-api backfill finished: $synced player(s) synced")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                logger.warn("mc-api backfill failed: ${e.message}")
            } finally {
                backfillRunning.set(false)
            }
        }, "NoticeConnect-McApiBackfill").apply { isDaemon = true }.start()
        return true
    }

    /** POST して 2xx なら true。例外は外に出さず false にする。 */
    private fun post(config: McApi, body: String): CompletableFuture<Boolean> {
        val request = try {
            HttpRequest.newBuilder(URI.create(config.url.trimEnd('/') + "/api/assign"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer ${config.token}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        } catch (e: IllegalArgumentException) {
            warnOnce("invalid mcApi.url")
            return CompletableFuture.completedFuture(false)
        }
        return Utils.httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .handle { response, error ->
                when {
                    error != null -> {
                        warnOnce(error.javaClass.simpleName)
                        false
                    }
                    response.statusCode() !in 200..299 -> {
                        warnOnce("HTTP ${response.statusCode()}")
                        false
                    }
                    else -> {
                        failureWarned.set(false)
                        true
                    }
                }
            }
    }

    private fun warnOnce(reason: String) {
        val logger = NoticeConnectPlatform.bridge.logger
        if (failureWarned.compareAndSet(false, true)) {
            logger.warn("Failed to sync players to mc-api ($reason). Further failures are logged at debug level until it recovers.")
        } else {
            logger.debug("Failed to sync players to mc-api ($reason)")
        }
    }

    internal fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    /** Discord の埋め込みに使うアバター画像の URL。 */
    fun avatarUrl(uuid: UUID, name: String): String {
        val configured = CustomConfig.config.discord.avatarUrl
        if (configured.isNotBlank()) {
            return configured.replace("%(UUID)", uuid.toString()).replace("%(PlayerName)", name)
        }
        val mcApi = CustomConfig.config.mcApi
        if (mcApi.enabled && mcApi.url.isNotBlank()) {
            return "${mcApi.url.trimEnd('/')}/avatar/$uuid"
        }
        return "https://crafthead.net/avatar/$uuid"
    }
}
