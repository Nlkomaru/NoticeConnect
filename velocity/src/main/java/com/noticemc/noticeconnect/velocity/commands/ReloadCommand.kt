package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.logic.Lifecycle
import com.noticemc.noticeconnect.velocity.NoticeConnectVelocity
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.velocity.annotation.CommandPermission
import revxrsal.commands.velocity.core.VelocityActor
import java.nio.file.Path

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.reload")
class ReloadCommand {
    private val dir: Path = NoticeConnectVelocity.path

    @Subcommand("reload")
    @Description("Reload NoticeConnect")
    fun reloadCommand(actor: VelocityActor) {
        actor.reply("Reloading...")
        val config = CustomConfig()
        config.getConfigFile(dir)
        Lifecycle.connectDatabase(dir, NoticeConnectVelocity.logger).thenAccept { connected ->
            actor.reply(if (connected) "Reloaded!" else "Reloaded, but failed to connect to the database")
        }
    }
}
