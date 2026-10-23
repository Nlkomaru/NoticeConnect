package com.noticemc.noticeconnect.paper.commands

import com.noticemc.noticeconnect.common.mcapi.McApiClient
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import revxrsal.commands.bukkit.core.BukkitActor

@Command("noticeconnect", "ntc")
@CommandPermission("noticeconnect.sync")
class SyncCommand {
    @Subcommand("sync")
    @Description("Sync players that have never been synced to mc-api")
    fun sync(actor: BukkitActor) {
        when {
            !McApiClient.isEnabled() -> actor.reply("mc-api is disabled (mcApi.enabled / url / token)")
            McApiClient.startBackfill() -> actor.reply("Started syncing players to mc-api. See the console for the result.")
            else -> actor.reply("Syncing is already running")
        }
    }
}
