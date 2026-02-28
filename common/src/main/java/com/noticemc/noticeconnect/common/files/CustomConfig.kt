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

        config = Hocon.decodeFromConfig(ConfigFactory.parseFile(file))
    }

    companion object {
        lateinit var config: Config
    }
}
