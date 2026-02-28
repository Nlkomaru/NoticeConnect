package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.common.utils.Utils.mm
import com.noticemc.noticeconnect.common.utils.Utils.toGsonText
import com.noticemc.noticeconnect.velocity.NoticeConnectVelocity
import com.noticemc.noticeconnect.velocity.commands.command.Suggestion
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.command.CommandActor
import revxrsal.commands.velocity.annotation.CommandPermission

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.transfer")
class TransferCommand {

    @Subcommand("transfer")
    @Description("Transfer a player to another server")
    fun transferCommand(
        actor: CommandActor,
        @Suggestion("server") server: RegisteredServer,
        @Suggestion("playerName") player: Player
    ) {
        if (player.currentServer.get().serverInfo.name == server.serverInfo.name) {
            actor.error(mm.deserialize("<red>Player is already on that server").toGsonText())
            return
        }
        NoticeConnectVelocity.proxy.getPlayer(player.uniqueId).get().createConnectionRequest(server).fireAndForget()
        actor.reply(mm.deserialize("<green>Transferred player to ${server.serverInfo.name}").toGsonText())
    }
}
