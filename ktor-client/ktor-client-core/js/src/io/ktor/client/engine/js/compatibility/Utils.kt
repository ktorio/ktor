/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.browser.*
import io.ktor.client.engine.js.node.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.browser.*
import kotlinx.coroutines.*
import org.w3c.fetch.*
import kotlin.coroutines.*
import kotlin.js.*

internal suspend fun commonFetch(
    input: String,
    init: RequestInit
): Response = suspendCancellableCoroutine { continuation ->
    val controller = if (PlatformUtils.IS_BROWSER) {
        AbortController()
    } else {
        NodeAbortController()
    }
    init.signal = controller.signal

    continuation.invokeOnCancellation {
        controller.abort()
    }

    val promise: Promise<Response> = if (PlatformUtils.IS_BROWSER) {
        window.fetch(input, init)
    } else {
        nodeFetch(input, init)
    }

    promise.then(
        onFulfilled = {
            continuation.resume(it)
        },
        onRejected = {
            continuation.resumeWithException(Error("Fail to fetch", it))
        }
    )
}

internal fun CoroutineScope.readBody(
    response: Response
): ByteReadChannel = if (PlatformUtils.IS_BROWSER) {
    readBodyBrowser(response)
} else {
    readBodyNode(response)
}
