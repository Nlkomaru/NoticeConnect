package com.noticemc.noticeconnect.paper

import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.logic.Lifecycle
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.paper.commands.HelpCommand
import com.noticemc.noticeconnect.paper.commands.MessageCommand
import com.noticemc.noticeconnect.paper.commands.ReloadCommand
import com.noticemc.noticeconnect.paper.commands.SyncCommand
import com.noticemc.noticeconnect.paper.events.PlayerJoinListener
import com.noticemc.noticeconnect.paper.events.PlayerQuitListener
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.ktx.supportSuspendFunctions

class NoticeConnectPaper : JavaPlugin() {

    override fun onEnable() {
        NoticeConnectPlatform.bridge = PaperPlatformBridge(this)

        CustomConfig().getConfigFile(dataFolder.toPath())
        // 起動時は移行が終わるまで待つ (参加イベントより先に DB を使えるようにするため)
        Lifecycle.connectDatabase(dataFolder.toPath(), NoticeConnectPlatform.bridge.logger).join()

        registerEvents()
        registerCommands()
    }

    override fun onDisable() {
        Lifecycle.shutdown()
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(PlayerJoinListener(), this)
        server.pluginManager.registerEvents(PlayerQuitListener(), this)
    }

    private fun registerCommands() {
        val handler = BukkitCommandHandler.create(this)

        handler.setSwitchPrefix("--")
        handler.setFlagPrefix("--")
        handler.supportSuspendFunctions()

        handler.setHelpWriter { command, _ ->
            java.lang.String.format(
                """
                <color:yellow>コマンド: <color:gray>%s %s
                <color:yellow>説明: <color:gray>%s

                """.trimIndent(),
                command.path.toRealString(),
                command.usage,
                command.description,
            )
        }

        with(handler) {
            register(ReloadCommand(this@NoticeConnectPaper))
            register(HelpCommand())
            register(MessageCommand())
            register(SyncCommand())
        }
    }

}
