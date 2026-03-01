package com.noticemc.noticeconnect.common.files

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.hocon.Hocon

@OptIn(ExperimentalSerializationApi::class)
val hocon = Hocon

@Serializable
data class DiscordMessage(val firstJoin: String, val join: String, val left: String)

@Serializable
data class Discord(val url: String, val message: DiscordMessage)

@Serializable
data class Message(val firstJoin: List<String>, val join: List<String>, val left: List<String>)

@Serializable
data class Database(val database: String)

@Serializable
data class Config(val version: String,
    val message: Message,
    val database: Database,
    val replace: Map<String, String>,
    val discord: Discord,
    val mode: String)
