package com.noticemc.noticeconnect.common.database

import com.noticemc.noticeconnect.common.files.CustomConfig
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class Database {

    fun connect(dataDirectory: Path?) {
        if (!isConnected()) {
            Class.forName("org.sqlite.JDBC")
            val file = File(dataDirectory.toString(), CustomConfig.config.database.database + ".db")
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.path)
        }
    }

    companion object {
        var connection: Connection? = null
        fun initializeDatabase() {
            try {
                val preparedStatement = connection?.prepareStatement("CREATE TABLE IF NOT EXISTS JoinedPlayerList (UUID VARCHAR(40) NOT NULL)")
                preparedStatement?.execute()
                preparedStatement?.close()
            } catch (ex: SQLException) {
                ex.printStackTrace()
            }
        }

        fun isConnected(): Boolean {
            return connection != null
        }

        fun disconnect() {
            if (isConnected()) {
                try {
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
