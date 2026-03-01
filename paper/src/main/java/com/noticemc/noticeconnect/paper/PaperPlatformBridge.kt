package com.noticemc.noticeconnect.paper

import com.noticemc.noticeconnect.common.platform.PlatformBridge
import com.noticemc.noticeconnect.common.utils.Utils.mm
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

class PaperPlatformBridge(
    private val plugin: JavaPlugin
) : PlatformBridge {

    override val dataDirectory: Path = plugin.dataFolder.toPath()
    override val logger: Logger = LoggerFactory.getLogger(plugin.name)

    override fun broadcastMessage(message: Component, mode: String) {
        val audience: Audience = Bukkit.getServer()

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
