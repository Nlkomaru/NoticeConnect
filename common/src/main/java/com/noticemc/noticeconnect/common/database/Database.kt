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

    /** LAST_SERVER の最大長。これより長いサーバー名は切り詰めて保存する。 */
    const val LAST_SERVER_LENGTH = 64

    val uuid = varchar("UUID", 40).uniqueIndex(UUID_INDEX_NAME)
    val name = varchar("NAME", 32).nullable()
    val firstJoinedAt = long("FIRST_JOINED_AT").nullable()
    val lastJoinedAt = long("LAST_JOINED_AT").nullable()
    val lastSyncedAt = long("LAST_SYNCED_AT").nullable()

    // ---- 以下はプレイ統計の列。旧データとの互換のため、すべて nullable で DB 側の既定値も持たせない ----
    // (既定値を付けると既存行に値が入ってしまうほか、MigrationUtils が SQLite で実行できない変更文を毎回出すため)

    /** 最終退出日時 (エポックミリ秒)。 */
    val lastLeftAt = long("LAST_LEFT_AT").nullable()

    /** 参加回数。null は 0 として扱う (この列ができる前の参加は数えていない)。 */
    val joinCount = integer("JOIN_COUNT").nullable()

    /** 累計プレイ時間 (秒)。null は 0 として扱う。 */
    val playtimeSeconds = long("PLAYTIME_SECONDS").nullable()

    /** 最後にいたサーバー名。 */
    val lastServer = varchar("LAST_SERVER", LAST_SERVER_LENGTH).nullable()
}

/**
 * プレイヤー名の履歴。(UUID, NAME) の組ごとに 1 行で、初めて見た日時と最後に見た日時を持つ。
 *
 * 新しいテーブルなので、無ければ [SchemaUtils.create] で作るだけでよい (既存データへの影響はない)。
 * この列ができる前の名前 (JoinedPlayerList.NAME にだけある値) は履歴に入れない。
 */
object PlayerNameHistory : Table("PlayerNameHistory") {
    const val UUID_NAME_INDEX_NAME = "PlayerNameHistory_UUID_NAME_unique"

    val uuid = varchar("UUID", 40)
    val name = varchar("NAME", 32)
    val firstSeenAt = long("FIRST_SEEN_AT")
    val lastSeenAt = long("LAST_SEEN_AT")

    init {
        uniqueIndex(UUID_NAME_INDEX_NAME, uuid, name)
    }
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
     * 参加を記録する。未登録なら行を追加し、登録済みなら名前・最終参加日時・参加回数・最後のサーバーを更新する。
     * 名前の履歴 ([PlayerNameHistory]) も更新する。
     *
     * @return 初参加 (行を追加した) なら true
     */
    fun recordJoin(uuid: UUID, name: String, serverName: String?, now: Long = System.currentTimeMillis()): Boolean =
        recordJoin(requireDb(), uuid, name, serverName, now)

    /**
     * 退出を記録する。最終退出日時を更新し、[sessionSeconds] が分かれば累計プレイ時間に足す。
     * 行が無い (参加を記録できていない) 場合は何もしない。
     */
    fun recordLeave(uuid: UUID, sessionSeconds: Long?, now: Long = System.currentTimeMillis()) =
        recordLeave(requireDb(), uuid, sessionSeconds, now)

    /** サーバーの移動 (Velocity) を記録する。行が無ければ何もしない。 */
    fun recordServer(uuid: UUID, serverName: String) = recordServer(requireDb(), uuid, serverName)

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
    /** まだ mc-api に送っていない行 (`LAST_SYNCED_AT IS NULL`) の件数。一括同期の進み具合のログに使う。 */
    fun countUnsynced(): Long = transaction(requireDb()) {
        JoinedPlayerList.selectAll().where { JoinedPlayerList.lastSyncedAt.isNull() }.count()
    }

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
     * 1. テーブル (JoinedPlayerList, PlayerNameHistory) が無ければ作る
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
        // 名前の履歴は新しいテーブルなので、無ければインデックスごと作るだけでよい。
        // MigrationUtils の対象には入れない (インデックスの差分が SQLite で実行できない文として毎回警告されるため)
        if (!PlayerNameHistory.exists()) {
            logger.info("Creating table ${PlayerNameHistory.tableName}")
            SchemaUtils.create(PlayerNameHistory)
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

    /**
     * 参加を記録する。呼び出しは DB スレッドに直列化されている前提で、
     * 「更新して 0 行なら追加」「読んでから書く」をひとつのトランザクションで行う。
     *
     * FIRST_JOINED_AT は行を追加するときにだけ入れる (この列ができる前からいる行は null のまま)。
     * JOIN_COUNT は null を 0 とみなして 1 足す (既存の行は次の参加で 1 から数え始める)。
     */
    internal fun recordJoin(database: ExposedDatabase, uuid: UUID, name: String, serverName: String?, now: Long): Boolean =
        transaction(database) {
            val key = uuid.toString()
            val server = serverName?.take(JoinedPlayerList.LAST_SERVER_LENGTH)
            // 重複した UUID が残っている旧 DB もあるので、最大値を基準にして全行を同じ値にそろえる
            val currentCounts = JoinedPlayerList.select(JoinedPlayerList.joinCount)
                .where { JoinedPlayerList.uuid eq key }
                .map { it[JoinedPlayerList.joinCount] }
            val firstJoin = if (currentCounts.isNotEmpty()) {
                val nextCount = (currentCounts.maxOf { it ?: 0 }) + 1
                JoinedPlayerList.update({ JoinedPlayerList.uuid eq key }) {
                    it[JoinedPlayerList.name] = name
                    it[lastJoinedAt] = now
                    it[joinCount] = nextCount
                    if (server != null) it[lastServer] = server
                }
                false
            } else {
                JoinedPlayerList.insert {
                    it[JoinedPlayerList.uuid] = key
                    it[JoinedPlayerList.name] = name
                    it[firstJoinedAt] = now
                    it[lastJoinedAt] = now
                    it[joinCount] = 1
                    it[lastServer] = server
                }
                true
            }
            recordName(key, name, now)
            firstJoin
        }

    /** 名前の履歴を upsert する。(UUID, NAME) が既にあれば LAST_SEEN_AT だけを更新する。 */
    private fun recordName(key: String, name: String, now: Long) {
        val updated = PlayerNameHistory.update({ (PlayerNameHistory.uuid eq key) and (PlayerNameHistory.name eq name) }) {
            it[lastSeenAt] = now
        }
        if (updated == 0) {
            PlayerNameHistory.insert {
                it[uuid] = key
                it[PlayerNameHistory.name] = name
                it[firstSeenAt] = now
                it[lastSeenAt] = now
            }
        }
    }

    internal fun recordLeave(database: ExposedDatabase, uuid: UUID, sessionSeconds: Long?, now: Long) {
        transaction(database) {
            val key = uuid.toString()
            val currentPlaytimes = JoinedPlayerList.select(JoinedPlayerList.playtimeSeconds)
                .where { JoinedPlayerList.uuid eq key }
                .map { it[JoinedPlayerList.playtimeSeconds] }
            if (currentPlaytimes.isEmpty()) return@transaction
            JoinedPlayerList.update({ JoinedPlayerList.uuid eq key }) {
                it[lastLeftAt] = now
                if (sessionSeconds != null) {
                    // null は 0 とみなして足す
                    it[playtimeSeconds] = currentPlaytimes.maxOf { value -> value ?: 0L } + sessionSeconds
                }
            }
        }
    }

    internal fun recordServer(database: ExposedDatabase, uuid: UUID, serverName: String) {
        transaction(database) {
            JoinedPlayerList.update({ JoinedPlayerList.uuid eq uuid.toString() }) {
                it[lastServer] = serverName.take(JoinedPlayerList.LAST_SERVER_LENGTH)
            }
        }
    }

    /** 1 回のセッションとして数える最大の長さ。これを超えるものは記録漏れ (退出を取りこぼした等) とみなして捨てる。 */
    const val MAX_SESSION_SECONDS: Long = 7L * 24 * 60 * 60

    /**
     * セッションの長さ (秒) を返す。開始が分からない、負になる、[MAX_SESSION_SECONDS] を超える場合は null。
     */
    fun sessionSeconds(startMillis: Long?, endMillis: Long): Long? {
        if (startMillis == null) return null
        val seconds = (endMillis - startMillis) / 1000
        if (seconds < 0 || seconds > MAX_SESSION_SECONDS) return null
        return seconds
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
