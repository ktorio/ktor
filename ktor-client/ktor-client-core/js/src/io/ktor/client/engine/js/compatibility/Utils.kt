/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.browser.*
import io.ktor.client.fetch.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

@OptIn(InternalCoroutinesApi::class)
internal suspend fun commonFetch(
    input: String,
    init: RequestInit,
    config: JsClientEngineConfig,
    callJob: Job,
): org.w3c.fetch.Response = suspendCancellableCoroutine { continuation ->
    val controller = AbortController()
    init.signal = controller.signal

    callJob.invokeOnCompletion(onCancelling = true) { controller.abort() }

    val promise: Promise<org.w3c.fetch.Response> = when {
        PlatformUtils.IS_BROWSER -> fetch(input, init)
        else -> {
            val options = js("Object").assign(js("Object").create(null), init, config.nodeOptions)
            fetch(input, options)
        }
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

private fun AbortController(): AbortController = js("new AbortController()")

internal fun CoroutineScope.readBody(
    response: org.w3c.fetch.Response
): ByteReadChannel =
    readBodyBrowser(response)
