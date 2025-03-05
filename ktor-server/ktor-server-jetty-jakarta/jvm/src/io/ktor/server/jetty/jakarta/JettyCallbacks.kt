/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import kotlinx.coroutines.CancellableContinuation
import kotlinx.io.IOException
import org.eclipse.jetty.util.Callback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Jetty works with a `Callback` type which succeeds or fails similar to a coroutine continuation.
 */
internal fun CancellableContinuation<Unit>.asCallback(): Callback = object : Callback {
    override fun failed(x: Throwable?) {
        resumeWithException(x ?: IOException("Failed with no exception"))
    }
    override fun succeeded() {
        resume(Unit)
    }
}
