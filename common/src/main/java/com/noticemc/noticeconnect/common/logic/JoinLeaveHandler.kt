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

object JoinLeaveHandler {

    /**
     * 参加時の処理。DB の記録 (初参加かどうかの判定を含む) をメインスレッドの外で行い、
     * その結果に応じてメッセージと Discord 通知を送り、mc-api に登録する。
     */
    fun handleJoin(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
        DatabaseExecutor.execute {
            // メッセージの有無に関係なく、参加は必ず記録する (名前と最終参加日時も更新する)
            val firstJoin = try {
                Database.recordJoin(uuid, playerName)
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

    fun handleLeave(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
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
