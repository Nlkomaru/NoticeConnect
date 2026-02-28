package com.noticemc.noticeconnect.common.logic

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.discord.Author
import com.noticemc.noticeconnect.common.discord.DiscordWebhookData
import com.noticemc.noticeconnect.common.discord.DiscordWebhookEmbed
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.utils.Utils.mm
import com.noticemc.noticeconnect.common.utils.Utils.sendAudienceMessage
import com.noticemc.noticeconnect.common.utils.Utils.sendWebHook
import org.apache.commons.lang3.StringUtils
import java.awt.Color
import java.sql.Connection
import java.util.*

object JoinLeaveHandler {

    fun handleJoin(uuid: UUID, playerName: String, serverName: String, hasPermissionHide: Boolean) {
        val replaceName = CustomConfig.config.replace[serverName] ?: serverName

        lateinit var discordJoin: String
        val color: Int

        if (playerExists(uuid)) {
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
                addPlayerLoginData(uuid)
            }
            color = Integer.valueOf(Integer.toHexString(Color.YELLOW.rgb).substring(2), 16)
            discordJoin = CustomConfig.config.discord.message.firstJoin.replace("%(PlayerName)", playerName)
        }
        if (discordJoin.isBlank()) {
            return
        }
        val author = Author(discordJoin, null, "https://crafthead.net/avatar/$uuid")
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
        val author = Author(discordLeftMessage, null, "https://crafthead.net/avatar/$uuid")
        val embed = DiscordWebhookEmbed(Integer.valueOf(Integer.toHexString(Color.RED.rgb).substring(2), 16), author)
        DiscordWebhookData(arrayListOf(embed)).sendWebHook()
    }

    fun playerExists(uuid: UUID): Boolean {
        var playerExists = false
        try {
            val connection: Connection = Database.connection ?: return true
            val statement = connection.prepareStatement("SELECT * FROM JoinedPlayerList WHERE uuid = ?")
            statement.setString(1, uuid.toString())
            val rs = statement.executeQuery()
            if (rs.next()) {
                playerExists = true
            }
            rs.close()
            statement.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return playerExists
    }

    fun addPlayerLoginData(uuid: UUID) {
        try {
            val connection: Connection? = Database.connection
            val statement = connection?.prepareStatement("INSERT INTO JoinedPlayerList (uuid) VALUES (?)")
            statement?.setString(1, uuid.toString())
            statement?.execute()
            statement?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
