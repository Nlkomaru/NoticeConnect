package com.noticemc.noticeconnect.paper.commands

import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.logic.Lifecycle
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.paper.NoticeConnectPaper
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import revxrsal.commands.bukkit.core.BukkitActor

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.reload")
class ReloadCommand(private val plugin: NoticeConnectPaper) {

    @Subcommand("reload")
    @Description("Reload NoticeConnect")
    fun reloadCommand(actor: BukkitActor) {
        actor.reply("Reloading...")
        val config = CustomConfig()
        config.getConfigFile(plugin.dataFolder.toPath())
        // DB の再接続と移行はメインスレッドの外で行い、終わったらメインスレッドで返信する
        Lifecycle.connectDatabase(plugin.dataFolder.toPath(), NoticeConnectPlatform.bridge.logger).thenAccept { connected ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                actor.reply(if (connected) "Reloaded!" else "Reloaded, but failed to connect to the database")
            })
        }
    }
}
