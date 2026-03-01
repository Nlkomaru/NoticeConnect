package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.common.utils.Utils.toGsonText
import net.kyori.adventure.text.minimessage.MiniMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.command.CommandActor
import revxrsal.commands.help.CommandHelp

@Command("noticeconnect", "ntc")
class HelpCommand {
    @Subcommand("help")
    @Description("Show help for NoticeConnect")
    fun help(actor: CommandActor, helpEntries: CommandHelp<String>, @Default("1") page: Int) {
        for (entry in helpEntries.paginate(page, 7)) {
            val mm = MiniMessage.miniMessage()
            val message = mm.deserialize(entry).toGsonText()
            actor.reply(message)
        }
    }
}
