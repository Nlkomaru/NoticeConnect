package com.noticemc.noticeconnect.common.files

import com.typesafe.config.ConfigFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import java.io.File
import java.nio.file.Path

class CustomConfig {
    @OptIn(ExperimentalSerializationApi::class)
    fun getConfigFile(dataDirectory: Path?) {
        val dataDirFile = dataDirectory!!.toFile()
        val file = File(dataDirFile, "config.conf")
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        SerializeConfig.main(file.toPath())

        config = decode(ConfigFactory.parseFile(file))
    }

    companion object {
        lateinit var config: Config

        /** 設定を読み込む。無いキーは data class の既定値になる (古い設定ファイルとの互換)。 */
        @OptIn(ExperimentalSerializationApi::class)
        fun decode(source: com.typesafe.config.Config): Config = Hocon.decodeFromConfig(source)
    }
}
