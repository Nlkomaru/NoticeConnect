package com.noticemc.noticeconnect.common.files

import com.typesafe.config.ConfigRenderOptions
import kotlinx.serialization.hocon.encodeToConfig
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists

// 注意: この値が設定ファイルの version と異なると、設定ファイルは既定値で上書きされる。
// 項目を追加しただけ (既定値付きで、古い設定ファイルのままでも読める) の場合は上げないこと。
private const val ver = "2.0.0"
private const val firstMessage = "<gray>[<yellow>初見さんいらっしゃい</yellow>] <light_purple><name>さんがはじめて<currentServerName>サーバーに入りました!"
private const val joinMessage = "<yellow><gold><name></gold>さんが<gold>サーバー<gold><gray>(<currentServerName>)</gray>にやってきました!"
private const val leftMessage = "<yellow><gold><name></gold>さんが<gold>サーバー<gold><gray>(<currentServerName>)</gray>から離れました"
private const val discordFirstJoin = "%(PlayerName) さんがサーバーに初参加です"
private const val discordJoin = "%(PlayerName) さんがサーバーに参加しました"
private const val discordLeft = "%(PlayerName) さんがサーバーから退出しました"
private const val defaultMode = "Chat"
private const val databaseName = "NoticeConnect"

object SerializeConfig {

    fun main(path: Path) {

        val message = Message(listOf(firstMessage), listOf(joinMessage), listOf(leftMessage))
        val database = Database(databaseName)
        val replace = HashMap<String, String>()
        val discord = Discord("", DiscordMessage(discordFirstJoin, discordJoin, discordLeft))
        val config = Config(ver, message, database, replace, discord, defaultMode)

        val encode = hocon.encodeToConfig(config)

        val renderOptions = ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setFormatted(true).setJson(false)
        val string = encode.root().render(renderOptions)

        if (!path.exists()) {
            path.createFile()
            val fw = PrintWriter(BufferedWriter(OutputStreamWriter(FileOutputStream(path.toFile()), "UTF-8")))
            fw.write(string)
            fw.close()
        } else {
            val loader = HoconConfigurationLoader.builder().path(path).build()
            val verNode = loader.load().node("version")
            if (verNode.string != ver) {
                path.toFile().deleteOnExit()
                val fw = PrintWriter(BufferedWriter(OutputStreamWriter(FileOutputStream(path.toFile()), "UTF-8")))
                fw.write(string)
                fw.close()
            }
        }
    }

}
