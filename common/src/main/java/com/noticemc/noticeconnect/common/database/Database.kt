package com.noticemc.noticeconnect.common.database

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import java.sql.Connection
import java.util.*
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase

/**
 * 参加したことのあるプレイヤーの一覧。
 *
 * 既存の DB と互換を保つため、テーブル名と `UUID` 列 (VARCHAR(40) NOT NULL、主キーなし) は旧実装のまま。
 * 旧データには "<uuid>-<数字>" のような 40 文字を超える値もあるが、SQLite は長さを強制しないので読める。
 * 追加の列はすべて nullable にしている (旧データには値がないため)。日時はエポックミリ秒。
 */
object JoinedPlayerList : Table("JoinedPlayerList") {
    /** UUID の一意インデックス名。重複があると張れないので、作成は [Database.migrate] が判断する。 */
    const val UUID_INDEX_NAME = "JoinedPlayerList_UUID_unique"

    val uuid = varchar("UUID", 40).uniqueIndex(UUID_INDEX_NAME)
    val name = varchar("NAME", 32).nullable()
    val firstJoinedAt = long("FIRST_JOINED_AT").nullable()
    val lastJoinedAt = long("LAST_JOINED_AT").nullable()
    val lastSyncedAt = long("LAST_SYNCED_AT").nullable()
}

/**
 * SQLite (Exposed) へのアクセスをまとめたもの。
 *
 * 呼び出しはメインスレッドを塞がないよう [DatabaseExecutor] 上で行うこと (起動時の接続を除く)。
 */
object Database {
    @Volatile
    private var db: ExposedDatabase? = null

    @Volatile
    private var connectedFile: File? = null

    /**
     * `<dataDirectory>/<databaseName>.db` に接続し、スキーマを移行する。
     * 同じファイルに接続済みなら移行だけを再実行する (移行は冪等)。
     */
    fun connect(dataDirectory: Path, databaseName: String, logger: Logger) {
        val file = File(dataDirectory.toFile(), "$databaseName.db")
        if (db == null || connectedFile?.absoluteFile != file.absoluteFile) {
            disconnect()
            file.parentFile?.mkdirs()
            db = connectFile(file)
            connectedFile = file
        }
        migrate(requireDb(), logger)
    }

    fun isConnected(): Boolean = db != null

    fun disconnect() {
        val current = db ?: return
        TransactionManager.closeAndUnregister(current)
        db = null
        connectedFile = null
    }

    private fun requireDb(): ExposedDatabase = db ?: error("Database is not connected")

    /** プレイヤー数 (行数) を返す。 */
    fun countPlayers(): Long = transaction(requireDb()) { JoinedPlayerList.selectAll().count() }

    /**
     * 参加を記録する。未登録なら行を追加し、登録済みなら名前と最終参加日時を更新する。
     *
     * @return 初参加 (行を追加した) なら true
     */
    fun recordJoin(uuid: UUID, name: String, now: Long = System.currentTimeMillis()): Boolean =
        recordJoin(requireDb(), uuid, name, now)

    /** mc-api への同期が完了したことを記録する。 */
    fun markSynced(uuids: Collection<String>, now: Long = System.currentTimeMillis()) {
        if (uuids.isEmpty()) return
        transaction(requireDb()) {
            JoinedPlayerList.update({ JoinedPlayerList.uuid inList uuids }) {
                it[lastSyncedAt] = now
            }
        }
    }

    /**
     * まだ mc-api に同期していない UUID を、[afterUuid] より後ろから UUID 順に最大 [limit] 件返す。
     * 形式が壊れた UUID も含まれるので、呼び出し側で除外する (カーソルで進めるので無限ループしない)。
     */
    fun unsyncedUuids(afterUuid: String, limit: Int): List<String> = transaction(requireDb()) {
        JoinedPlayerList.select(JoinedPlayerList.uuid)
            .where { JoinedPlayerList.lastSyncedAt.isNull() and (JoinedPlayerList.uuid greater afterUuid) }
            .orderBy(JoinedPlayerList.uuid, SortOrder.ASC)
            .limit(limit)
            .map { it[JoinedPlayerList.uuid] }
    }

    // ---- 以下はテストからも使う、接続先を引数に取る実装 ----

    /** SQLite ファイルに接続する。SQLite はトランザクション分離レベルを SERIALIZABLE にする必要がある。 */
    internal fun connectFile(file: File): ExposedDatabase = ExposedDatabase.connect(
        url = "jdbc:sqlite:${file.path}",
        driver = "org.sqlite.JDBC",
        databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE },
    )

    /**
     * スキーマを最新にする。冪等で、何度実行してもよい。
     *
     * 1. テーブルが無ければ作る
     * 2. Exposed の MigrationUtils でテーブル定義との差分を出し、列の追加 (ALTER TABLE ... ADD) だけを実行する。
     *    UUID のインデックスは 3 で扱う。SQLite は列の変更・制約の追加ができないため、それ以外の文は実行せずに警告する
     * 3. UUID に重複が無ければ一意インデックスを張る (重複があれば警告してスキップ)
     *
     * @return 実行した SQL
     */
    internal fun migrate(database: ExposedDatabase, logger: Logger): List<String> = transaction(database) {
        val executed = mutableListOf<String>()
        // テーブルが無いとき (新規の DB) だけ作る。既存のテーブルに対しては何もしない
        if (!JoinedPlayerList.exists()) {
            SchemaUtils.create(JoinedPlayerList)
        }

        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(JoinedPlayerList, withLogs = false)
        for (statement in statements) {
            if (statement.contains(JoinedPlayerList.UUID_INDEX_NAME)) {
                // インデックスは重複を確認してから下で作る
                continue
            } else if (isAddColumn(statement)) {
                logger.info("Migrating database: $statement")
                exec(statement)
                executed += statement
            } else {
                logger.warn("Skipped unsupported migration statement for SQLite: $statement")
            }
        }

        val duplicates = exec(
            "SELECT COUNT(*) FROM (SELECT UUID FROM JoinedPlayerList GROUP BY UUID HAVING COUNT(*) > 1)"
        ) { rs -> if (rs.next()) rs.getInt(1) else 0 } ?: 0
        if (duplicates == 0) {
            if (!indexExists(JoinedPlayerList.UUID_INDEX_NAME)) {
                val statement = "CREATE UNIQUE INDEX IF NOT EXISTS ${JoinedPlayerList.UUID_INDEX_NAME} ON JoinedPlayerList (UUID)"
                logger.info("Migrating database: $statement")
                exec(statement)
                executed += statement
            }
        } else {
            logger.warn("JoinedPlayerList has $duplicates duplicated UUID(s); skipped creating the unique index on UUID")
        }
        executed
    }

    internal fun recordJoin(database: ExposedDatabase, uuid: UUID, name: String, now: Long): Boolean =
        transaction(database) {
            val key = uuid.toString()
            val updated = JoinedPlayerList.update({ JoinedPlayerList.uuid eq key }) {
                it[JoinedPlayerList.name] = name
                it[lastJoinedAt] = now
            }
            if (updated > 0) {
                false
            } else {
                JoinedPlayerList.insert {
                    it[JoinedPlayerList.uuid] = key
                    it[JoinedPlayerList.name] = name
                    it[firstJoinedAt] = now
                    it[lastJoinedAt] = now
                }
                true
            }
        }

    private fun isAddColumn(statement: String): Boolean {
        val normalized = statement.trim().uppercase()
        return normalized.startsWith("ALTER TABLE") && normalized.contains(" ADD ")
    }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.indexExists(name: String): Boolean =
        exec("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = '$name'") { rs ->
            rs.next() && rs.getInt(1) > 0
        } ?: false
}
