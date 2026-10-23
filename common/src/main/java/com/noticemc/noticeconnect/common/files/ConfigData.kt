package com.noticemc.noticeconnect.common.files

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.hocon.Hocon

/**
 * 設定ファイルの書き出し用。既定値を持つ項目 (mcApi など) も省略せずに書き出す。
 */
@OptIn(ExperimentalSerializationApi::class)
val hocon = Hocon { encodeDefaults = true }

@Serializable
data class DiscordMessage(val firstJoin: String, val join: String, val left: String)

/**
 * @property avatarUrl 埋め込みのアイコン URL。`%(UUID)` と `%(PlayerName)` を置換する。
 *   空なら mcApi が有効のとき `<mcApi.url>/v1/players/<uuid>/avatar?size=64`、無効のとき crafthead を使う。
 */
@Serializable
data class Discord(val url: String, val message: DiscordMessage, val avatarUrl: String = "")

@Serializable
data class Message(val firstJoin: List<String>, val join: List<String>, val left: List<String>)

@Serializable
data class Database(val database: String)

/**
 * mc-api との連携設定。既存の設定ファイルにはこのブロックが無いため、すべて既定値を持たせている。
 */
@Serializable
data class McApi(
    val enabled: Boolean = false,
    val url: String = "https://mc-api.morinoparty.workers.dev",
    val token: String = "",
)

@Serializable
data class Config(val version: String,
    val message: Message,
    val database: Database,
    val replace: Map<String, String>,
    val discord: Discord,
    val mode: String,
    val mcApi: McApi = McApi())
