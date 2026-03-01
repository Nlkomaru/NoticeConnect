package com.noticemc.noticeconnect.velocity.commands

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.files.CustomConfig
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
    var sql: Database? = null
    private val dir: Path = NoticeConnectVelocity.path

    @Subcommand("reload")
    @Description("Reload NoticeConnect")
    fun reloadCommand(actor: VelocityActor) {
        actor.reply("Reloading...")
        val config = CustomConfig()
        config.getConfigFile(dir)
        sqlConnection()
        actor.reply("Reloaded!")
    }

    private fun sqlConnection() {
        sql = Database()
        try {
            sql!!.connect(dir)
        } catch (e: Exception) {
            NoticeConnectVelocity.logger.error("Failed to connect to database")
            e.printStackTrace()
        }
        if (Database.isConnected()) {
            NoticeConnectVelocity.logger.info("Connected to database")
        }
        Database.initializeDatabase()
    }
}
