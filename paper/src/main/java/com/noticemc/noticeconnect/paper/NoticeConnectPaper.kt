package com.noticemc.noticeconnect.paper

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.platform.NoticeConnectPlatform
import com.noticemc.noticeconnect.paper.commands.HelpCommand
import com.noticemc.noticeconnect.paper.commands.MessageCommand
import com.noticemc.noticeconnect.paper.commands.ReloadCommand
import com.noticemc.noticeconnect.paper.events.PlayerJoinListener
import com.noticemc.noticeconnect.paper.events.PlayerQuitListener
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.ktx.supportSuspendFunctions
import java.sql.Connection

class NoticeConnectPaper : JavaPlugin() {
    private var sql: Database? = null

    override fun onEnable() {
        NoticeConnectPlatform.bridge = PaperPlatformBridge(this)

        CustomConfig().getConfigFile(dataFolder.toPath())
        sqlConnection()
        logger.info("今までに${joinedPlayerCount()}人のプレイヤーがサーバーを訪れました")

        registerEvents()
        registerCommands()
    }

    override fun onDisable() {
        Database.disconnect()
    }

    private fun joinedPlayerCount(): Int {
        var count = 0
        try {
            val connection: Connection? = Database.connection
            val statement = connection?.prepareStatement("SELECT COUNT(*) FROM JoinedPlayerList")
            count = statement?.executeQuery()?.getInt(1) ?: 0
            statement?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
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
        }
    }

    private fun sqlConnection() {
        sql = Database()
        try {
            sql!!.connect(dataFolder.toPath())
        } catch (e: Exception) {
            logger.severe("Failed to connect to database")
            e.printStackTrace()
        }
        if (Database.isConnected()) {
            logger.info("Connected to database")
        }
        Database.initializeDatabase()
    }
}
