package com.noticemc.noticeconnect.common.logic

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.database.DatabaseExecutor
import com.noticemc.noticeconnect.common.discord.Author
import com.noticemc.noticeconnect.common.discord.DiscordWebhookData
import com.noticemc.noticeconnect.common.discord.DiscordWebhookEmbed
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.mcapi.McApiClient
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.common.utils.Utils.mm
import com.noticemc.noticeconnect.common.utils.Utils.sendAudienceMessage
import com.noticemc.noticeconnect.common.utils.Utils.sendWebHook
import org.apache.commons.lang3.StringUtils
import java.awt.Color
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 参加・退出・サーバー移動の処理。
 *
 * ## 通知の順序
 * 参加の通知は「初参加かどうか」を DB で判定してから送るので、DB スレッド ([DatabaseExecutor]) で行う。
 * 退出の通知をイベントスレッドからそのまま送ると、すぐに切断した場合に「退出」が「参加」より先に出てしまう。
 * そのため退出も (DB への記録と合わせて) 同じ DB スレッドに流す。DB スレッドは 1 本の FIFO なので、
 * 先に積まれた参加の処理 (記録と通知) が終わってから退出の処理が走り、順序が必ずそろう。
 * イベントスレッドはタスクを積むだけで、DB や HTTP を待たない。
 *
 * 既知の UUID をメモリに持って初参加をイベントスレッドで判定する方法もあるが、起動時の全件読み込みと
 * DB との二重管理が必要になるので採らなかった。
 *
 * なお停止後に積まれたタスクは捨てられるため、停止時にオンラインだったプレイヤーの
 * 退出 (LAST_LEFT_AT とそのセッションのプレイ時間) は記録されない。
 */
object JoinLeaveHandler {

    /**
     * セッションの開始時刻 (エポックミリ秒)。参加時にイベントスレッドで入れ、退出時に取り出す。
     * DB の処理が詰まっていてもプレイ時間がずれないよう、時刻はイベントの時点で取る。
     * 再起動・リロードをまたいだセッションは開始が分からないので、プレイ時間には数えない。
     */
    private val sessionStarts = ConcurrentHashMap<UUID, Long>()

    /**
     * 参加時の処理。DB の記録 (初参加かどうかの判定を含む) をメインスレッドの外で行い、
     * その結果に応じてメッセージと Discord 通知を送り、mc-api に登録する。
     */
    fun handleJoin(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
        val now = System.currentTimeMillis()
        sessionStarts[uuid] = now
        DatabaseExecutor.execute {
            // メッセージの有無に関係なく、参加は必ず記録する (名前・最終参加日時・参加回数・サーバー・名前の履歴)
            val firstJoin = try {
                Database.recordJoin(uuid, playerName, serverName, now)
            } catch (e: Exception) {
                // DB が使えないときは旧実装と同じく「参加済み」として扱う
                NoticeConnectPlatform.bridge.logger.error("Failed to record the join of $playerName ($uuid)", e)
                false
            }
            try {
                announceJoin(uuid, playerName, serverName, hasPermissionHide, firstJoin)
            } catch (e: Exception) {
                NoticeConnectPlatform.bridge.logger.error("Failed to announce the join of $playerName", e)
            }
            McApiClient.assign(uuid, playerName)
        }
    }

    private fun announceJoin(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean, firstJoin: Boolean) {
        val replaceName = CustomConfig.config.replace[serverName] ?: serverName

        val discordJoin: String
        val color: Int

        if (!firstJoin) {
            val loginMessage: String = CustomConfig.config.message.join.random()

            if (!StringUtils.isBlank(loginMessage) && !hasPermissionHide) {
                val replacedMessage = loginMessage.replace("<name>", playerName).replace("<currentServerName>", replaceName)
                sendAudienceMessage(mm.deserialize(replacedMessage))
            }
            color = Integer.valueOf(Integer.toHexString(Color.GREEN.rgb).substring(2), 16)
            discordJoin = CustomConfig.config.discord.message.join.replace("%(PlayerName)", playerName)
        } else {
            val loginMessage: String = CustomConfig.config.message.firstJoin.random()
            if (!StringUtils.isBlank(loginMessage)) {
                val replacedMessage = loginMessage.replace("<name>", playerName).replace("<currentServerName>", replaceName)
                sendAudienceMessage(mm.deserialize(replacedMessage))
            }
            color = Integer.valueOf(Integer.toHexString(Color.YELLOW.rgb).substring(2), 16)
            discordJoin = CustomConfig.config.discord.message.firstJoin.replace("%(PlayerName)", playerName)
        }
        if (discordJoin.isBlank()) {
            return
        }
        val author = Author(discordJoin, null, McApiClient.avatarUrl(uuid, playerName))
        val embed = DiscordWebhookEmbed(color, author)
        DiscordWebhookData(arrayListOf(embed)).sendWebHook()
    }

    /**
     * 退出時の処理。セッションの長さはイベントスレッドで確定させ、記録と通知は DB スレッドで行う
     * (参加の通知より先に退出の通知が出ないようにするため。クラスのコメントを参照)。
     */
    fun handleLeave(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
        val now = System.currentTimeMillis()
        val sessionSeconds = Database.sessionSeconds(sessionStarts.remove(uuid), now)
        DatabaseExecutor.execute {
            try {
                Database.recordLeave(uuid, sessionSeconds, now)
            } catch (e: Exception) {
                NoticeConnectPlatform.bridge.logger.error("Failed to record the leave of $playerName ($uuid)", e)
            }
            try {
                announceLeave(uuid, playerName, serverName, hasPermissionHide)
            } catch (e: Exception) {
                NoticeConnectPlatform.bridge.logger.error("Failed to announce the leave of $playerName", e)
            }
        }
    }

    /** サーバーの移動 (Velocity) を LAST_SERVER に記録する。通知はしない。 */
    fun handleServerSwitch(uuid: UUID, serverName: String) {
        DatabaseExecutor.execute {
            try {
                Database.recordServer(uuid, serverName)
            } catch (e: Exception) {
                NoticeConnectPlatform.bridge.logger.error("Failed to record the server of $uuid", e)
            }
        }
    }

    private fun announceLeave(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
        val replaceName = CustomConfig.config.replace[serverName] ?: serverName

        val leftMessage: String = CustomConfig.config.message.left.random()

        if (!StringUtils.isBlank(leftMessage) && !hasPermissionHide) {
            val replacedMessage = leftMessage.replace("<name>", playerName).replace("<currentServerName>", replaceName)
            sendAudienceMessage(mm.deserialize(replacedMessage))
        }

        val discordLeftMessage = CustomConfig.config.discord.message.left.replace("%(PlayerName)", playerName)
        if (discordLeftMessage.isBlank()) {
            return
        }
        val author = Author(discordLeftMessage, null, McApiClient.avatarUrl(uuid, playerName))
        val embed = DiscordWebhookEmbed(Integer.valueOf(Integer.toHexString(Color.RED.rgb).substring(2), 16), author)
        DiscordWebhookData(arrayListOf(embed)).sendWebHook()
    }
}
