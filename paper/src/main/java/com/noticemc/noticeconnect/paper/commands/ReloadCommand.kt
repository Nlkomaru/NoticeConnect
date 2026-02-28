package com.noticemc.noticeconnect.paper.commands

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.paper.NoticeConnectPaper
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import revxrsal.commands.bukkit.core.BukkitActor

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.reload")
class ReloadCommand(private val plugin: NoticeConnectPaper) {
    private var sql: Database? = null

    @Subcommand("reload")
    @Description("Reload NoticeConnect")
    fun reloadCommand(actor: BukkitActor) {
        actor.reply("Reloading...")
        val config = CustomConfig()
        config.getConfigFile(plugin.dataFolder.toPath())
        sqlConnection()
        actor.reply("Reloaded!")
    }

    private fun sqlConnection() {
        sql = Database()
        try {
            sql!!.connect(plugin.dataFolder.toPath())
        } catch (e: Exception) {
            plugin.logger.severe("Failed to connect to database")
            e.printStackTrace()
        }
        if (Database.isConnected()) {
            plugin.logger.info("Connected to database")
        }
        Database.initializeDatabase()
    }
}
