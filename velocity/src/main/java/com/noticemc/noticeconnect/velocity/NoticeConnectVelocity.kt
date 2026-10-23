package com.noticemc.noticeconnect.velocity

import com.google.inject.Inject
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.logic.Lifecycle
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.common.utils.Utils.mm
import com.noticemc.noticeconnect.common.utils.Utils.toGsonText
import com.noticemc.noticeconnect.velocity.commands.*
import com.noticemc.noticeconnect.velocity.commands.command.PlayerServerParser.registerPlayerParser
import com.noticemc.noticeconnect.velocity.commands.command.RegisteredServerParser.registerServerParser
import com.noticemc.noticeconnect.velocity.events.PlayerJoinEvent
import com.noticemc.noticeconnect.velocity.events.PlayerLeftEvent
import com.noticemc.noticeconnect.velocity.events.PlayerLoginEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.CommandParameter
import revxrsal.commands.command.ExecutableCommand
import revxrsal.commands.ktx.supportSuspendFunctions
import revxrsal.commands.velocity.VelocityCommandHandler
import java.nio.file.Path

class NoticeConnectVelocity {

    @Inject
    fun noticeConnect(server: ProxyServer, logger: Logger, @DataDirectory dataDirectory: Path) {
        proxy = server
        Companion.logger = logger
        path = dataDirectory

        NoticeConnectPlatform.bridge = VelocityPlatformBridge(proxy, logger, path)

        CustomConfig().getConfigFile(path)
        // 起動時は移行が終わるまで待つ (参加イベントより先に DB を使えるようにするため)
        Lifecycle.connectDatabase(path, logger).join()
    }

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent?) {
        registerEvents()
        registerCommands()
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent?) {
        Lifecycle.shutdown()
    }

    private fun registerEvents() {
        proxy.eventManager?.register(this, PlayerJoinEvent())
        proxy.eventManager?.register(this, PlayerLeftEvent())
        proxy.eventManager?.register(this, PlayerLoginEvent())
    }

    private fun registerCommands() {
        val handler = VelocityCommandHandler.create(this, proxy)

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

        handler.autoCompleter.registerSuggestionFactory { parameter: CommandParameter ->
            val annotation = parameter.getAnnotation(
                com.noticemc.noticeconnect.velocity.commands.command.Suggestion::class.java
            ) ?: return@registerSuggestionFactory null
            val string = annotation.value
            if (string == "server") {
                return@registerSuggestionFactory SuggestionProvider { _: List<String>, _: CommandActor, _: ExecutableCommand ->
                    return@SuggestionProvider proxy.allServers.map { it.serverInfo.name }
                }
            } else if (string == "playerName") {
                return@registerSuggestionFactory SuggestionProvider { _: List<String>, _: CommandActor, _: ExecutableCommand ->
                    return@SuggestionProvider proxy.allPlayers.map { it.username }
                }
            }
            null
        }
        handler.registerPlayerParser()
        handler.registerServerParser()

        with(handler) {
            register(ReloadCommand())
            register(HelpCommand())
            register(MessageCommand())
            register(TransferCommand())
            register(PlayerCommand())
            register(SyncCommand())
        }
    }


    companion object {
        lateinit var proxy: ProxyServer
        lateinit var logger: Logger
        lateinit var path: Path
    }
}
