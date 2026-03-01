package com.noticemc.noticeconnect.paper.commands

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.message")
class MessageCommand {

    @Subcommand("broadcast")
    @Description("Send a message to all player")
    fun messageCommand(message: String) {
        Bukkit.getServer().sendMessage(MiniMessage.miniMessage().deserialize(message))
    }
}
