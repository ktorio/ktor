/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import kotlinx.coroutines.*
import org.eclipse.jetty.util.*
import kotlin.coroutines.*

internal suspend fun <R> withPromise(
    block: (Promise<R>) -> Unit
): R = suspendCancellableCoroutine { continuation ->
    block(PromiseContinuation(continuation))
}

internal class PromiseContinuation<R>(private val continuation: Continuation<R>) : Promise<R> {
    override fun failed(x: Throwable) {
        continuation.resumeWithException(x)
    }

    override fun succeeded(result: R) {
        continuation.resume(result)
    }
}
