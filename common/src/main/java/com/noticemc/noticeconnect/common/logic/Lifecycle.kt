package com.noticemc.noticeconnect.common.logic

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.database.DatabaseExecutor
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.mcapi.McApiClient
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Velocity と Paper で共通の、起動・リロード・停止の処理。
 */
object Lifecycle {

    /**
     * DB に接続してスキーマを移行し、プレイヤー数をログに出す。処理は DB スレッドで行う。
     * mc-api が有効なら、未同期の行の一括同期も始める。
     *
     * @return 成功したら true になる future
     */
    fun connectDatabase(dataDirectory: Path, logger: Logger): CompletableFuture<Boolean> =
        DatabaseExecutor.submit {
            try {
                Database.connect(dataDirectory, CustomConfig.config.database.database, logger)
                logger.info("Connected to database")
                logger.info("今までに${Database.countPlayers()}人のプレイヤーがサーバーを訪れました")
                true
            } catch (e: Exception) {
                logger.error("Failed to connect to database", e)
                false
            }
        }.thenApply { connected ->
            if (connected) McApiClient.startBackfill()
            connected
        }

    /** mc-api の一括同期を止め、DB の処理を流し切ってから接続を閉じる。 */
    fun shutdown() {
        // 一括同期のスレッドは DB executor を使うので、先に止める
        McApiClient.shutdown()
        DatabaseExecutor.shutdown()
        Database.disconnect()
    }
}
