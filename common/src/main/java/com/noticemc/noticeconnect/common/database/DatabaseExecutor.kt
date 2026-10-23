package com.noticemc.noticeconnect.common.database

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DB 処理を流す単一スレッドの executor。
 *
 * Paper の PlayerJoinEvent はメインスレッドで呼ばれるため、DB アクセスはここに逃がす。
 * 単一スレッドにすることで SQLite への書き込みが直列化され、ロック競合も起きない。
 */
object DatabaseExecutor {
    @Volatile
    private var executor: ExecutorService = newExecutor()

    private fun newExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NoticeConnect-Database").apply { isDaemon = true }
    }

    fun execute(task: () -> Unit) {
        currentExecutor().execute(task)
    }

    fun <T> submit(task: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(task, currentExecutor())

    @Synchronized
    private fun currentExecutor(): ExecutorService {
        if (executor.isShutdown) {
            executor = newExecutor()
        }
        return executor
    }

    /** 残っている処理を最大 5 秒待ってから止める。プラグインの無効化時に呼ぶ。 */
    @Synchronized
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
