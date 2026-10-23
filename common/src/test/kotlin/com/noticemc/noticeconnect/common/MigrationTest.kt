package com.noticemc.noticeconnect.common

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.database.JoinedPlayerList
import com.noticemc.noticeconnect.common.files.CustomConfig
import com.noticemc.noticeconnect.common.files.McApi
import com.noticemc.noticeconnect.common.files.SerializeConfig
import com.noticemc.noticeconnect.common.mcapi.McApiClient
import com.typesafe.config.ConfigFactory
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.*
import kotlin.test.*

class MigrationTest {
    private val logger = LoggerFactory.getLogger("MigrationTest")
    private val tempDir: File = Files.createTempDirectory("noticeconnect-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /** 旧実装と同じスキーマの DB を raw JDBC で作る。 */
    private fun createLegacyDb(file: File, uuids: List<String>) {
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS JoinedPlayerList (UUID VARCHAR(40) NOT NULL)") }
            connection.prepareStatement("INSERT INTO JoinedPlayerList (UUID) VALUES (?)").use { statement ->
                for (uuid in uuids) {
                    statement.setString(1, uuid)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun describe(file: File): Pair<Int, List<String>> =
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            val count = connection.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM JoinedPlayerList").use { rs -> rs.next(); rs.getInt(1) }
            }
            val columns = connection.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(JoinedPlayerList)").use { rs ->
                    buildList { while (rs.next()) add("${rs.getString("name")} ${rs.getString("type")}") }
                }
            }
            count to columns
        }

    @Test
    fun migratesLegacyDatabaseWithoutDataLoss() {
        val file = File(tempDir, "legacy.db")
        val existing = UUID.randomUUID().toString()
        // 旧データには "<uuid>-<数字>" のような 56 文字の値も入っている
        val broken = "2a622c64-3690-4780-9d3d-a852b6150d44-14408787031297640071"
        createLegacyDb(file, listOf(existing, broken, UUID.randomUUID().toString()))

        val db = Database.connectFile(file)
        try {
            val executed = Database.migrate(db, logger)
            assertTrue(executed.any { it.contains("NAME") }, "NAME column should be added: $executed")
            assertTrue(executed.any { it.contains("UNIQUE INDEX") }, "unique index should be created: $executed")

            val (count, columns) = describe(file)
            assertEquals(3, count)
            assertEquals(
                listOf("UUID", "NAME", "FIRST_JOINED_AT", "LAST_JOINED_AT", "LAST_SYNCED_AT"),
                columns.map { it.substringBefore(' ') },
            )

            // 2 回目は何もしない (冪等)
            assertEquals(emptyList(), Database.migrate(db, logger))

            // 既存プレイヤーは更新、新規プレイヤーは追加
            assertFalse(Database.recordJoin(db, UUID.fromString(existing), "Steve", 1000L))
            val newcomer = UUID.randomUUID()
            assertTrue(Database.recordJoin(db, newcomer, "Alex", 2000L))
            assertFalse(Database.recordJoin(db, newcomer, "Alex2", 3000L))

            transaction(db) {
                val old = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq existing }.single()
                assertEquals("Steve", old[JoinedPlayerList.name])
                assertNull(old[JoinedPlayerList.firstJoinedAt])
                assertEquals(1000L, old[JoinedPlayerList.lastJoinedAt])

                val new = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq newcomer.toString() }.single()
                assertEquals("Alex2", new[JoinedPlayerList.name])
                assertEquals(2000L, new[JoinedPlayerList.firstJoinedAt])
                assertEquals(3000L, new[JoinedPlayerList.lastJoinedAt])
            }
            assertEquals(4, describe(file).first)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun skipsUniqueIndexWhenDuplicatesExist() {
        val file = File(tempDir, "dup.db")
        val uuid = UUID.randomUUID().toString()
        createLegacyDb(file, listOf(uuid, uuid))
        val db = Database.connectFile(file)
        try {
            val executed = Database.migrate(db, logger)
            assertTrue(executed.none { it.contains("UNIQUE INDEX") })
            assertEquals(2, describe(file).first)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun createsNewDatabase() {
        val file = File(tempDir, "new.db")
        val db = Database.connectFile(file)
        try {
            Database.migrate(db, logger)
            assertEquals(emptyList(), Database.migrate(db, logger))
            assertTrue(Database.recordJoin(db, UUID.randomUUID(), "Notch", 1L))
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    /**
     * 実データのコピーでの検証。NOTICECONNECT_SAMPLE_DB に DB のパスを指定したときだけ動く。
     * 元のファイルは変更しない (一時ディレクトリにコピーして移行する)。
     */
    @Test
    fun migratesSampleDatabaseCopy() {
        val samplePath = System.getenv("NOTICECONNECT_SAMPLE_DB") ?: return
        val copy = File(tempDir, "sample.db")
        Files.copy(File(samplePath).toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val before = describe(copy)
        println("[sample] before: rows=${before.first} columns=${before.second}")
        val db = Database.connectFile(copy)
        try {
            val executed = Database.migrate(db, logger)
            println("[sample] executed: $executed")
            val after = describe(copy)
            println("[sample] after: rows=${after.first} columns=${after.second}")
            assertEquals(before.first, after.first)
            val second = Database.migrate(db, logger)
            println("[sample] second run executed: $second")
            assertEquals(emptyList(), second)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun decodesConfigWithoutMcApiBlock() {
        // mcApi ブロックも discord.avatarUrl も無い、既存の設定ファイル相当
        val legacy = """
            database { database = NoticeConnect }
            discord {
                message { firstJoin = "a", join = "b", left = "c" }
                url = ""
            }
            message { firstJoin = ["x"], join = ["y"], left = ["z"] }
            mode = Chat
            replace { lobby = "ロビー" }
            version = "2.0.0"
        """.trimIndent()
        val config = CustomConfig.decode(ConfigFactory.parseString(legacy))
        assertEquals(McApi(), config.mcApi)
        assertFalse(config.mcApi.enabled)
        assertEquals("", config.discord.avatarUrl)
    }

    @Test
    fun writesMcApiBlockToDefaultConfig() {
        val file = File(tempDir, "config.conf")
        SerializeConfig.main(file.toPath())
        val text = file.readText()
        assertTrue(text.contains("mcApi"), text)
        val config = CustomConfig.decode(ConfigFactory.parseFile(file))
        assertEquals("https://mc-api.morinoparty.workers.dev", config.mcApi.url)
    }

    @Test
    fun detectsCanonicalUuids() {
        assertTrue(McApiClient.isCanonicalUuid("00068061-a5fc-4e10-abb3-43e23006cf40"))
        assertFalse(McApiClient.isCanonicalUuid("2a622c64-3690-4780-9d3d-a852b6150d44-14408787031297640071"))
        assertFalse(McApiClient.isCanonicalUuid("1-1-1-1-1"))
    }
}
