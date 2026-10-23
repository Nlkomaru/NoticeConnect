package com.noticemc.noticeconnect.common

import com.noticemc.noticeconnect.common.database.Database
import com.noticemc.noticeconnect.common.database.JoinedPlayerList
import com.noticemc.noticeconnect.common.database.PlayerNameHistory
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
    companion object {
        /** 現在の JoinedPlayerList の列 (この順で ALTER TABLE ... ADD される)。 */
        val ALL_COLUMNS = listOf(
            "UUID", "NAME", "FIRST_JOINED_AT", "LAST_JOINED_AT", "LAST_SYNCED_AT",
            "LAST_LEFT_AT", "JOIN_COUNT", "PLAYTIME_SECONDS", "LAST_SERVER",
        )
    }

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

    /**
     * 前回の移行 (NAME 〜 LAST_SYNCED_AT の追加と UUID の一意インデックス) まで済んだ DB を raw JDBC で作る。
     * PlayerNameHistory はまだ無い。
     */
    private fun createPreviouslyMigratedDb(file: File, rows: List<Pair<String, String?>>) {
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.createStatement().use { st ->
                st.execute("CREATE TABLE JoinedPlayerList (UUID VARCHAR(40) NOT NULL)")
                st.execute("ALTER TABLE JoinedPlayerList ADD NAME VARCHAR(32) NULL")
                st.execute("ALTER TABLE JoinedPlayerList ADD FIRST_JOINED_AT BIGINT NULL")
                st.execute("ALTER TABLE JoinedPlayerList ADD LAST_JOINED_AT BIGINT NULL")
                st.execute("ALTER TABLE JoinedPlayerList ADD LAST_SYNCED_AT BIGINT NULL")
                st.execute("CREATE UNIQUE INDEX JoinedPlayerList_UUID_unique ON JoinedPlayerList (UUID)")
            }
            connection.prepareStatement(
                "INSERT INTO JoinedPlayerList (UUID, NAME, FIRST_JOINED_AT, LAST_JOINED_AT, LAST_SYNCED_AT) VALUES (?, ?, 10, 20, 30)"
            ).use { statement ->
                for ((uuid, name) in rows) {
                    statement.setString(1, uuid)
                    statement.setString(2, name)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun tableExists(file: File, table: String): Boolean =
        DriverManager.getConnection("jdbc:sqlite:${file.path}").use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?").use { st ->
                st.setString(1, table)
                st.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
            }
        }

    /** テスト用に DB へ接続して移行し、[block] を実行してから閉じる。 */
    private fun <T> withMigratedDb(file: File, block: (org.jetbrains.exposed.v1.jdbc.Database) -> T): T {
        val db = Database.connectFile(file)
        try {
            Database.migrate(db, logger)
            return block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
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
                ALL_COLUMNS,
                columns.map { it.substringBefore(' ') },
            )
            assertTrue(tableExists(file, "PlayerNameHistory"))

            // 2 回目は何もしない (冪等)
            assertEquals(emptyList(), Database.migrate(db, logger))

            // 既存プレイヤーは更新、新規プレイヤーは追加
            assertFalse(Database.recordJoin(db, UUID.fromString(existing), "Steve", "lobby", 1000L))
            val newcomer = UUID.randomUUID()
            assertTrue(Database.recordJoin(db, newcomer, "Alex", "lobby", 2000L))
            assertFalse(Database.recordJoin(db, newcomer, "Alex2", "main", 3000L))

            transaction(db) {
                val old = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq existing }.single()
                assertEquals("Steve", old[JoinedPlayerList.name])
                assertNull(old[JoinedPlayerList.firstJoinedAt])
                assertEquals(1000L, old[JoinedPlayerList.lastJoinedAt])

                val new = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq newcomer.toString() }.single()
                assertEquals("Alex2", new[JoinedPlayerList.name])
                assertEquals(2000L, new[JoinedPlayerList.firstJoinedAt])
                assertEquals(3000L, new[JoinedPlayerList.lastJoinedAt])
                // 既存の行は次の参加で 1 から数え始める
                assertEquals(1, old[JoinedPlayerList.joinCount])
                assertEquals("lobby", old[JoinedPlayerList.lastServer])
                assertEquals(2, new[JoinedPlayerList.joinCount])
                assertEquals("main", new[JoinedPlayerList.lastServer])
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
            assertTrue(Database.recordJoin(db, UUID.randomUUID(), "Notch", null, 1L))
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun migratesPreviouslyMigratedDatabase() {
        val file = File(tempDir, "previous.db")
        val existing = UUID.randomUUID().toString()
        createPreviouslyMigratedDb(file, listOf(existing to "Steve", UUID.randomUUID().toString() to null))
        assertFalse(tableExists(file, "PlayerNameHistory"))

        val db = Database.connectFile(file)
        try {
            val executed = Database.migrate(db, logger)
            // 新しい 4 列だけが追加され、既存のインデックスには触れない
            assertEquals(4, executed.size, "$executed")
            assertTrue(executed.all { it.uppercase().startsWith("ALTER TABLE") }, "$executed")
            for (column in listOf("LAST_LEFT_AT", "JOIN_COUNT", "PLAYTIME_SECONDS", "LAST_SERVER")) {
                assertTrue(executed.any { it.contains(column) }, "$column should be added: $executed")
            }
            assertTrue(tableExists(file, "PlayerNameHistory"))

            val (count, columns) = describe(file)
            assertEquals(2, count)
            assertEquals(ALL_COLUMNS, columns.map { it.substringBefore(' ') })
            assertEquals(emptyList(), Database.migrate(db, logger))

            transaction(db) {
                // 既存の値は残り、新しい列は null のまま
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq existing }.single()
                assertEquals("Steve", row[JoinedPlayerList.name])
                assertEquals(10L, row[JoinedPlayerList.firstJoinedAt])
                assertEquals(30L, row[JoinedPlayerList.lastSyncedAt])
                assertNull(row[JoinedPlayerList.joinCount])
                assertNull(row[JoinedPlayerList.playtimeSeconds])
                assertNull(row[JoinedPlayerList.lastLeftAt])
                assertNull(row[JoinedPlayerList.lastServer])
                // 既存の NAME から名前の履歴は作らない
                assertEquals(0L, PlayerNameHistory.selectAll().count())
            }

            // 移行済みの DB で参加回数が null → 1 → 2 と増える
            assertFalse(Database.recordJoin(db, UUID.fromString(existing), "Steve", "lobby", 100L))
            assertFalse(Database.recordJoin(db, UUID.fromString(existing), "Steve", "lobby", 200L))
            transaction(db) {
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq existing }.single()
                assertEquals(2, row[JoinedPlayerList.joinCount])
                // FIRST_JOINED_AT は行の追加時にしか入れない
                assertEquals(10L, row[JoinedPlayerList.firstJoinedAt])
            }
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun upsertsNameHistory() {
        withMigratedDb(File(tempDir, "names.db")) { db ->
            val uuid = UUID.randomUUID()
            Database.recordJoin(db, uuid, "Steve", "lobby", 1000L)
            Database.recordJoin(db, uuid, "Steve", "lobby", 2000L)
            Database.recordJoin(db, uuid, "Steve2", "lobby", 3000L)
            Database.recordJoin(db, uuid, "Steve", "lobby", 4000L)
            // 別のプレイヤーが同じ名前を使っても別の行になる
            Database.recordJoin(db, UUID.randomUUID(), "Steve", "lobby", 5000L)

            transaction(db) {
                val rows = PlayerNameHistory.selectAll()
                    .where { PlayerNameHistory.uuid eq uuid.toString() }
                    .associate { it[PlayerNameHistory.name] to (it[PlayerNameHistory.firstSeenAt] to it[PlayerNameHistory.lastSeenAt]) }
                assertEquals(mapOf("Steve" to (1000L to 4000L), "Steve2" to (3000L to 3000L)), rows)
                assertEquals(3L, PlayerNameHistory.selectAll().count())

                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq uuid.toString() }.single()
                assertEquals("Steve", row[JoinedPlayerList.name])
                assertEquals(4, row[JoinedPlayerList.joinCount])
            }
        }
    }

    @Test
    fun accumulatesPlaytime() {
        withMigratedDb(File(tempDir, "playtime.db")) { db ->
            val uuid = UUID.randomUUID()
            Database.recordJoin(db, uuid, "Alex", "lobby", 0L)
            Database.recordLeave(db, uuid, 60L, 60_000L)
            Database.recordLeave(db, uuid, 30L, 200_000L)
            // 開始が分からないセッションは時間を足さず、退出日時だけ更新する
            Database.recordLeave(db, uuid, null, 300_000L)
            Database.recordServer(db, uuid, "main")
            // 行の無いプレイヤーの退出は無視する
            Database.recordLeave(db, UUID.randomUUID(), 10L, 400_000L)

            transaction(db) {
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq uuid.toString() }.single()
                assertEquals(90L, row[JoinedPlayerList.playtimeSeconds])
                assertEquals(300_000L, row[JoinedPlayerList.lastLeftAt])
                assertEquals("main", row[JoinedPlayerList.lastServer])
                assertEquals(1L, JoinedPlayerList.selectAll().count())
            }
        }
    }

    @Test
    fun accumulatesPlaytimeOnLegacyRow() {
        val file = File(tempDir, "legacy-playtime.db")
        val uuid = UUID.randomUUID()
        createLegacyDb(file, listOf(uuid.toString()))
        withMigratedDb(file) { db ->
            // 参加を記録する前 (JOIN_COUNT も PLAYTIME_SECONDS も null) でも、null を 0 として足す
            Database.recordLeave(db, uuid, 15L, 1000L)
            transaction(db) {
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq uuid.toString() }.single()
                assertEquals(15L, row[JoinedPlayerList.playtimeSeconds])
                assertNull(row[JoinedPlayerList.joinCount])
            }
        }
    }

    @Test
    fun truncatesLongServerName() {
        withMigratedDb(File(tempDir, "server.db")) { db ->
            val uuid = UUID.randomUUID()
            Database.recordJoin(db, uuid, "Alex", "s".repeat(100), 0L)
            transaction(db) {
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq uuid.toString() }.single()
                assertEquals(JoinedPlayerList.LAST_SERVER_LENGTH, row[JoinedPlayerList.lastServer]?.length)
            }
        }
    }

    @Test
    fun computesSessionSeconds() {
        assertEquals(90L, Database.sessionSeconds(10_000L, 100_000L))
        assertEquals(0L, Database.sessionSeconds(1_000L, 1_500L))
        assertNull(Database.sessionSeconds(null, 100_000L))
        assertNull(Database.sessionSeconds(100_000L, 10_000L))
        val week = Database.MAX_SESSION_SECONDS * 1000
        assertEquals(Database.MAX_SESSION_SECONDS, Database.sessionSeconds(0L, week))
        assertNull(Database.sessionSeconds(0L, week + 1000))
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
            assertEquals(ALL_COLUMNS, after.second.map { it.substringBefore(' ') })
            assertTrue(tableExists(copy, "PlayerNameHistory"))
            // コピーに対して参加と退出を記録できること
            val player = UUID.randomUUID()
            assertTrue(Database.recordJoin(db, player, "SampleTester", "lobby", 1000L))
            Database.recordLeave(db, player, 42L, 43_000L)
            transaction(db) {
                val row = JoinedPlayerList.selectAll().where { JoinedPlayerList.uuid eq player.toString() }.single()
                assertEquals(1, row[JoinedPlayerList.joinCount])
                assertEquals(42L, row[JoinedPlayerList.playtimeSeconds])
                println("[sample] name history rows=${PlayerNameHistory.selectAll().count()}")
            }
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
