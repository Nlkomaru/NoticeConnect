package com.noticemc.noticeconnect.velocity.events

import com.noticemc.noticeconnect.common.logic.JoinLeaveHandler
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPostConnectEvent

class PlayerJoinEvent {
    @Subscribe
    fun onJoin(event: ServerPostConnectEvent) {
        val player = event.player
        if (!PlayerLoginEvent.list.remove(player.uniqueId)) {
            return
        }
        if (event.previousServer != null) {
            return
        }

        val serverName = player.currentServer.get().serverInfo.name
        val hasPermissionHide = player.hasPermission("noticeconnect.hide.join")

        JoinLeaveHandler.handleJoin(player.uniqueId, player.username, serverName, hasPermissionHide)
    }
}
