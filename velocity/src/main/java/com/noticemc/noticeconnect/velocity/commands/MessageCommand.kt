package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.velocity.NoticeConnectVelocity.Companion.proxy
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.message")
class MessageCommand {

    @Subcommand("broadcast")
    @Description("Send a message to all player")
    fun messageCommand(message: String) {
        val audience: Audience = proxy
        audience.sendMessage(MiniMessage.miniMessage().deserialize(message))
    }
}
