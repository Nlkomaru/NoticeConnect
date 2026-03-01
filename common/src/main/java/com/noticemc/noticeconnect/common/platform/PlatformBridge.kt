package com.noticemc.noticeconnect.common.platform

import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.nio.file.Path

interface PlatformBridge {
    val dataDirectory: Path
    val logger: Logger
    fun broadcastMessage(message: Component, mode: String)
}

object NoticeConnectPlatform {
    lateinit var bridge: PlatformBridge
}
