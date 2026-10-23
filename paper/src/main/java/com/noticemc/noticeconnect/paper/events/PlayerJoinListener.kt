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

        // Paper にはプロキシのようなサーバー名が無いので、Bukkit のサーバー名を使う。
        // <currentServerName> の置換と LAST_SERVER の記録の両方にこの値を使う
        val serverName = player.server.name
        val hasPermissionHide = player.hasPermission("noticeconnect.hide.join")

        JoinLeaveHandler.handleJoin(player.uniqueId, player.name, serverName, hasPermissionHide)
    }
}
