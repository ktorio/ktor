/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.browser.*
import io.ktor.client.fetch.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.js.Promise

internal suspend fun commonFetch(
    input: String,
    init: RequestInit,
    config: JsClientEngineConfig,
): org.w3c.fetch.Response = suspendCancellableCoroutine { continuation ->
    val promise: Promise<org.w3c.fetch.Response> = when {
        PlatformUtils.IS_BROWSER -> fetch(input, init)
        else -> {
            val options = js("Object").assign(js("Object").create(null), init, config.nodeOptions)
            fetch(input, options)
        }
    }

    promise.then(
        onFulfilled = {
            continuation.resumeWith(Result.success(it))
        },
        onRejected = {
            continuation.resumeWith(Result.failure(Error("Fail to fetch", it)))
        }
    )
}

internal fun AbortController(): AbortController {
    return js("new AbortController()")
}

internal fun CoroutineScope.readBody(
    response: org.w3c.fetch.Response
): ByteReadChannel =
    readBodyBrowser(response)
