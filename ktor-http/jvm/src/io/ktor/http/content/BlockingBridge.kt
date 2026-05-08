/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Redispatches [block] onto [Dispatchers.IO] for blocking I/O.
 *
 * This is used by non-blocking engines (CIO, Netty) where the calling thread is an event-loop
 * thread that must not block. Servlet-based engines bypass this entirely via
 * [OutputStreamContent.writeTo] with a native [java.io.OutputStream].
 */
internal suspend fun withBlocking(block: suspend () -> Unit) {
    withContext(Dispatchers.IO) {
        block()
    }
}
