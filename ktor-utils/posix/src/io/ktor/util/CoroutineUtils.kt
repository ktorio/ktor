/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*

@InternalAPI
public fun Dispatchers.createFixedThreadDispatcher(name: String, threads: Int): CloseableCoroutineDispatcher =
    MultiWorkerDispatcher(name, threads)

@OptIn(InternalAPI::class)
private class MultiWorkerDispatcher(name: String, workersCount: Int) : CloseableCoroutineDispatcher() {
    private val tasksQueue = Channel<Runnable>(Channel.UNLIMITED)
    private val workers = Array(workersCount) { Worker.start(name = "$name-$it") }

    init {
        workers.forEach { worker ->
            worker.executeAfter(0L) {
                ThreadInfo.registerCurrentThread()
                workerRunLoop()
            }
        }
    }

    private fun workerRunLoop() = runBlocking {
        for (task in tasksQueue) {
            task.run()
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasksQueue.trySend(block)
    }

    override fun close() {
        tasksQueue.close()
        workers.forEach { it.requestTermination().result }
    }
}
