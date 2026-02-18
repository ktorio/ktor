/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers

/**
 * Creates [CoroutineDispatcher] for the client with fixed [threadCount] and specified [dispatcherName].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.utils.clientDispatcher)
 *
 * @param threadCount the number of threads for the new [CoroutineDispatcher].
 * @param dispatcherName the name of the new [CoroutineDispatcher].
 */
@OptIn(DelicateCoroutinesApi::class)
@Deprecated(
    "Use Dispatchers.IO.limitedParallelism instead",
    ReplaceWith(
        "Dispatchers.IO.limitedParallelism(threadCount)",
        "kotlinx.coroutines.Dispatchers",
    )
)
@InternalAPI
public expect fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String = "ktor-client-dispatcher"
): CoroutineDispatcher
