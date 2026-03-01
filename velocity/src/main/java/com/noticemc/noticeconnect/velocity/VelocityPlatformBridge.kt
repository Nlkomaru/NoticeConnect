package com.noticemc.noticeconnect.velocity

import com.noticemc.noticeconnect.common.platform.PlatformBridge
import com.noticemc.noticeconnect.common.utils.Utils.mm
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.slf4j.Logger
import java.nio.file.Path

class VelocityPlatformBridge(
    private val proxy: ProxyServer,
    override val logger: Logger,
    override val dataDirectory: Path
) : PlatformBridge {

    override fun broadcastMessage(message: Component, mode: String) {
        val audience: Audience = proxy

        when (mode) {
            "ActionBar" -> {
                audience.sendActionBar(message)
            }
            "MainTitle" -> {
                audience.showTitle(Title.title(message, mm.deserialize("")))
            }
            "SubTitle" -> {
                audience.showTitle(Title.title(mm.deserialize(""), message))
            }
            else -> {
                audience.sendMessage(message)
            }
        }
    }
}
