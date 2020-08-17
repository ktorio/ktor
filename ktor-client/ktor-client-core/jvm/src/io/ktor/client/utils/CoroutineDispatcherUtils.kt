/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*

/**
 * Creates [CoroutineDispatcher] based on thread pool of [threadCount] threads.
 */
@OptIn(InternalCoroutinesApi::class)
@InternalAPI
actual fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String
): CoroutineDispatcher = ExperimentalCoroutineDispatcher(threadCount, threadCount, dispatcherName)

/**
 * Creates [CoroutineDispatcher] based on thread pool of [threadCount] threads.
 */
@Suppress("unused")
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
fun Dispatchers.fixedThreadPoolDispatcher(
    threadCount: Int,
    dispatcherName: String = "client-dispatcher"
): CoroutineDispatcher {
    return clientDispatcher(threadCount, dispatcherName)
}
