package com.noticemc.noticeconnect.paper.events

import com.noticemc.noticeconnect.common.logic.JoinLeaveHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // バニラの参加メッセージを抑制
        event.joinMessage(null)

        val serverName = player.server.name
        val hasPermissionHide = player.hasPermission("noticeconnect.hide.join")

        JoinLeaveHandler.handleJoin(player.uniqueId, player.name, serverName, hasPermissionHide)
    }
}
