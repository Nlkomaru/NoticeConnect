package com.noticemc.noticeconnect.velocity.events

import com.noticemc.noticeconnect.common.logic.JoinLeaveHandler
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent

/**
 * プロキシ内でのサーバー移動を LAST_SERVER に記録する。
 * 最初の接続 (移動元が無い) は [PlayerJoinEvent] の参加処理で記録するので、ここでは扱わない。
 */
class PlayerServerSwitchEvent {
    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        if (event.previousServer.isEmpty) {
            return
        }
        JoinLeaveHandler.handleServerSwitch(event.player.uniqueId, event.server.serverInfo.name)
    }
}
