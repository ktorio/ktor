/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Creates [CoroutineDispatcher] based on thread pool of [threadCount] threads.
 */
@InternalAPI
fun Dispatchers.fixedThreadPoolDispatcher(threadCount: Int, threadName: String = "thread-pool-%d"): CoroutineDispatcher {
    val threadsNum = AtomicInteger(0)
    return Executors.newFixedThreadPool(threadCount) {
        Thread(it).apply {
            isDaemon = true
            name = threadName.format(threadsNum.getAndIncrement())
        }
    }.asCoroutineDispatcher()
}
