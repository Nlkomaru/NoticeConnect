package com.noticemc.noticeconnect.common.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordWebhookData(val embeds: ArrayList<DiscordWebhookEmbed>)

@Serializable
data class DiscordWebhookEmbed(var color: Int, var author: Author)

@Serializable
data class Author(val name: String, val url: String? = null, @SerialName("icon_url") val iconUrl: String)
