/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import kotlinx.coroutines.*
import java.net.*

/**
 * Infinite timeout in milliseconds.
 */
internal const val INFINITE_TIMEOUT_MS = Long.MAX_VALUE

/**
 * Wrap [block] into [withTimeout] wrapper and throws [SocketTimeoutException] if timeout exceeded.
 */
internal suspend fun CoroutineScope.withSocketTimeout(socketTimeout: Long, block: suspend CoroutineScope.() -> Unit) {
    if (socketTimeout == INFINITE_TIMEOUT_MS) {
        block()
    } else {
        async {
            withTimeoutOrNull(socketTimeout, block) ?: throw SocketTimeoutException()
        }.await()
    }
}
