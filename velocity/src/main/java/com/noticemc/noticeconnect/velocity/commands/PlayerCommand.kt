package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.velocity.NoticeConnectVelocity
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.core.VelocityActor

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.player")
class PlayerCommand {
    @Subcommand("player")
    fun playerCommand(actor: VelocityActor) {
        val proxy = NoticeConnectVelocity.proxy
        val servers = proxy.allServers
        servers.forEach { server ->
            val serverName = server.serverInfo.name
            val players = server.playersConnected.joinToString(", ") { it.username }
            val message = "$serverName $players"
            actor.reply(message)
        }
    }
}
