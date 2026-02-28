package com.noticemc.noticeconnect.velocity.events

import com.noticemc.noticeconnect.common.logic.JoinLeaveHandler
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent

class PlayerLeftEvent {
    @Subscribe
    fun onLeft(event: DisconnectEvent) {
        val player = event.player
        if (player.currentServer.isEmpty) {
            return
        }

        val serverName = player.currentServer.get().serverInfo.name ?: "server"
        val hasPermissionHide = player.hasPermission("noticeconnect.hide.left")

        JoinLeaveHandler.handleLeave(player.uniqueId, player.username, serverName, hasPermissionHide)
    }
}
