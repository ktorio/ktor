/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.*
import kotlinx.coroutines.*
import org.w3c.fetch.*
import kotlin.coroutines.*
import kotlin.js.*

internal suspend fun commonFetch(
    input: String,
    init: RequestInit,
    platformApi: JsPlatformApi
): Response = suspendCancellableCoroutine { continuation ->
    val controller = platformApi.createAbortController()
    init.signal = controller.signal

    continuation.invokeOnCancellation {
        controller.abort()
    }

    val promise: Promise<Response> = platformApi.fetch(input, init)

    promise.then(
        onFulfilled = {
            continuation.resume(it)
        },
        onRejected = {
            continuation.resumeWithException(Error("Fail to fetch", it))
        }
    )
}
