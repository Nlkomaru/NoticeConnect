# NoticeConnect

A login message plugin that runs on Velocity

[![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)](http://creativecommons.org/publicdomain/zero/1.0/)

To the extent possible under law,
[<span property="dct:title">Nikomaru</span>](https://github.com/Nlkomaru/)
has waived all copyright and related or neighboring rights to
<span property="dct:title">NoticeConnect</span>.
This work is published from:
<span property="vcard:Country" datatype="dct:ISO3166" content="JP" about="https://github.com/Nlkomaru/NoticeConnect">日本</span>.

## Transfar Hint

Please run it in your own kotlin environment.
You will also need the server directory and the database output location.

```Kotlin
//Written in 2022  by Nikomaru <nikomaru@nikomaru.dev>
//
//To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.
//This software is distributed without any warranty.
//
//You should have received a copy of the CC0 Public Domain Dedication along with this software.
//If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

fun main(args: Array<String>) {

    Class.forName("org.sqlite.JDBC")
    val connection: Connection = DriverManager.getConnection("jdbc:sqlite:Any Directory${File.separator}NoticeConnect.db")
    val path = "The path to the server's world directory ${File.separator}playerdata${File.separator}"
    val dir: Path = Path.of(path)
    try {
        val preparedStatement: PreparedStatement =
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS JoinedPlayerList (UUID VARCHAR(40) NOT NULL)")
        preparedStatement.execute()
        preparedStatement.close()
    } catch (ex: SQLException) {
        ex.printStackTrace()
    }

    var counter = 0
    var counter1 = 0
    try {
        Files.list(dir).use { stream ->
            stream.forEach { p: Path ->
                if (!p.toString().contains("dat_old")) {
                    val uuid = p.toString().replace(path, "").replace(".dat", "")
                    println(uuid)
                    val statement = connection.prepareStatement("INSERT into JoinedPlayerList (UUID) VALUES (?)")
                    statement.setString(1, uuid)
                    statement.execute()
                    statement.close()
                    counter++
                }
                counter1++
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    connection.close()
    println("$counter $counter1 players added to the database")

}
```

## Config

```hocon
database {
    database = NoticeConnect
}
discord {
    ## The message that will be sent to the discord channel.
    message {
        firstJoin = "%(PlayerName) さんがサーバーに初参加です"
        join = "%(PlayerName) さんがサーバーに参加しました"
        left = "%(PlayerName) さんがサーバーから退出しました"
    }
    url = "https://discord.com/api/webhooks/*********/**************"
    ## Icon URL of the embed. %(UUID) and %(PlayerName) are replaced.
    ## When empty: <mcApi.url>/avatar/<uuid> if mcApi is enabled, otherwise https://crafthead.net/avatar/<uuid>
    avatarUrl = ""
}
## mc-api integration. On every join, POST <url>/api/assign with {"uuid", "name"}
## (Authorization: Bearer <token>). Only used when enabled = true and url / token are set.
mcApi {
    enabled = false
    url = "https://mc-api.morinoparty.workers.dev"
    token = ""
}
message {
    ## The message that will be sent to players on the server, randomly selected from an array
    ## Tag <name> = PlayerName  <currentServerName> = The name of the server to connect to ("lobby", "main", etc.).
    firstJoin = [
        "<#ec407a><name></#ec407a>さんがはじめてサーバーにやってきました！"
        
    ]
    join = [
        "<#9ccc65><name></#9ccc65>がサーバーにやってきました！"
        "<#9ccc65><name></#9ccc65>さんがサーバーに参加しました！"
    ]
    left = [
        "<#03a9f4><name></#03a9f4>さんがサーバーから退出しました！"
        "<#03a9f4><name></#03a9f4>、またね～"
    ]
}
## The mode of the message that will be sent to players on the server. (ActionBar, Chat, MainTitle, SubTitle)
mode = ActionBar
## The replacement for the message that will be sent to players on the server
replace {
    lobby = "ロビー"
    main = "生活"
    res = "資源"
}
## Config Version Dont touch this
version = "2.0.0"
```

`mcApi` and `discord.avatarUrl` are optional. Config files written by older versions keep working without them
(the defaults above are used); add the blocks manually to enable mc-api.

## mc-api

When `mcApi.enabled` is `true`, NoticeConnect registers players to mc-api.

- On every join (Velocity: first connection to the proxy, Paper: `PlayerJoinEvent`), it sends
  `POST <mcApi.url>/api/assign` with `{"uuid": "<uuid>", "name": "<name>"}` asynchronously (10s timeout).
  Failures are logged once at warn level until a request succeeds again. The token is never logged.
- On startup / reload, and with `/noticeconnect sync` (permission `noticeconnect.sync`), rows that have never been synced
  (`LAST_SYNCED_AT IS NULL`) are sent in batches of 100 (`{"uuids": [...], "fetch": false}`), one batch per second.
  Malformed UUIDs in old data are skipped. If a batch fails, the sync stops and resumes on the next run.

## Database

The SQLite file is `<data directory>/<database.database>.db`, accessed with [Exposed](https://github.com/JetBrains/Exposed).

Table `JoinedPlayerList`:

| Column | Type | Description |
| --- | --- | --- |
| `UUID` | VARCHAR(40) NOT NULL | Player UUID (unique index when there are no duplicates) |
| `NAME` | VARCHAR(32) NULL | Last known player name (empty for rows recorded before this column existed) |
| `FIRST_JOINED_AT` | BIGINT NULL | First join time (epoch millis). Empty for rows recorded before this column existed |
| `LAST_JOINED_AT` | BIGINT NULL | Last join time (epoch millis) |
| `LAST_SYNCED_AT` | BIGINT NULL | Last time the player was registered to mc-api (epoch millis) |

Every join is recorded: a new row is inserted on the first join, otherwise `NAME` and `LAST_JOINED_AT` are updated.

On startup the schema is migrated automatically and without data loss: missing columns are added with
`ALTER TABLE ... ADD` (computed by Exposed's `MigrationUtils`), and the unique index on `UUID` is created only if
there are no duplicated UUIDs (otherwise a warning is logged). The migration is idempotent.
