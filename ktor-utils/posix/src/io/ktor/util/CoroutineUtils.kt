/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@InternalAPI
public fun Dispatchers.createFixedThreadDispatcher(name: String, threads: Int): CloseableCoroutineDispatcher =
    MultiWorkerDispatcher(name, threads)

@OptIn(InternalAPI::class)
private class MultiWorkerDispatcher(name: String, workersCount: Int) : CloseableCoroutineDispatcher() {
    private val closed = atomic(false)
    private val tasksQueue = Channel<Runnable>(Channel.UNLIMITED)
    private val workers = Array(workersCount) { Worker.start(name = "$name-$it") }
    private val futures = mutableListOf<Future<Unit>>()

    init {
        for (worker in workers) {
            worker.execute(TransferMode.SAFE, { }) {
                ThreadInfo.registerCurrentThread()
            }.consume()

            futures += worker.execute(TransferMode.SAFE, { this }) {
                it.workerRunLoop()
            }
        }
    }

    private fun workerRunLoop() = runBlocking {
        for (task in tasksQueue) {
            task.run()
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (closed.value) return

        val result = tasksQueue.trySendBlocking(block)
        if (result.isSuccess) return

        throw IllegalStateException("Fail to dispatch task", result.exceptionOrNull())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val closeWorker = Worker.start(errorReporting = true, name = "Close worker")
        closeWorker.execute(TransferMode.SAFE, { this }) {
            it.tasksQueue.close()

            it.futures.forEach {
                it.consume()
            }

            it.futures.clear()

            it.workers.forEach { worker ->
                ThreadInfo.dropWorker(worker)
                kotlin.runCatching {
                    worker.requestTermination().consume()
                }
            }
        }
    }
}

private fun <T> Future<T>.consume() {
    consume { }
}
