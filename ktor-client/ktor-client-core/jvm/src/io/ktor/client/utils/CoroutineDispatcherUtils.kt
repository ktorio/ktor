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
@UseExperimental(InternalCoroutinesApi::class)
@InternalAPI
fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String = "thread-pool-%d"
): CoroutineDispatcher = ExperimentalCoroutineDispatcher(threadCount, threadCount, dispatcherName)
