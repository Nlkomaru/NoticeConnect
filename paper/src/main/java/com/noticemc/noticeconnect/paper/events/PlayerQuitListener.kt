package com.noticemc.noticeconnect.paper.events

import com.noticemc.noticeconnect.common.logic.JoinLeaveHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerQuitListener : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player

        // バニラの退出メッセージを抑制
        event.quitMessage(null)

        val serverName = player.server.name
        val hasPermissionHide = player.hasPermission("noticeconnect.hide.left")

        JoinLeaveHandler.handleLeave(player.uniqueId, player.name, serverName, hasPermissionHide)
    }
}
