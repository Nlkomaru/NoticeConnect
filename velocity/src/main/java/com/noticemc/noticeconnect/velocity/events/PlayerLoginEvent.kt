package com.noticemc.noticeconnect.velocity.events

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import java.util.*

class PlayerLoginEvent {

    @Subscribe
    fun onPlayerLogin(event: PlayerChooseInitialServerEvent) {
        val player = event.player
        list.add(player.uniqueId)
    }

    companion object {
        var list = HashSet<UUID>()
    }
}
