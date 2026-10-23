package com.noticemc.noticeconnect.common.database

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * DB 処理を流す単一スレッドの executor。
 *
 * Paper の PlayerJoinEvent はメインスレッドで呼ばれるため、DB アクセスはここに逃がす。
 * 単一スレッドにすることで SQLite への書き込みが直列化され、ロック競合も起きない。
 */
object DatabaseExecutor {
    // 停止後は作り直さない (Paper の /reload で古いクラスローダーのスレッドが残らないようにするため)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NoticeConnect-Database").apply { isDaemon = true }
    }

    /** 処理を DB スレッドに流す。停止後に呼ばれた場合は何もしない。 */
    fun execute(task: () -> Unit) {
        try {
            executor.execute(task)
        } catch (e: RejectedExecutionException) {
            // プラグインの停止中に来たイベントは捨てる
        }
    }

    /** 停止後に呼ぶと [RejectedExecutionException] を投げる。 */
    fun <T> submit(task: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(task, executor)

    /** 残っている処理を最大 5 秒待ってから止める。プラグインの無効化時に呼ぶ。 */
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
