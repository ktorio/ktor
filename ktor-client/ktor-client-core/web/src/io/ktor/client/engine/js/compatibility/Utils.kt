/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.browser.readBodyBrowser
import io.ktor.client.utils.*
import io.ktor.util.*
import io.ktor.utils.io.*
import js.errors.JsErrorLike
import js.promise.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import web.abort.AbortController
import web.http.RequestInit
import web.http.Response
import web.http.fetchAsync
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.js
import kotlin.js.unsafeCast

@Suppress("DEPRECATION")
@OptIn(InternalCoroutinesApi::class)
internal suspend fun commonFetch(
    input: String,
    init: RequestInit,
    requestConfig: RequestInit.() -> Unit,
    config: JsClientEngineConfig,
    callJob: Job,
): Response = suspendCancellableCoroutine { continuation ->
    val controller = AbortController()
    init.signal = controller.signal
    config.requestInit(init)
    requestConfig(init)

    callJob.invokeOnCompletion(onCancelling = true) { controller.abort() }

    val promise: Promise<Response> = when {
        PlatformUtils.IS_BROWSER -> fetchAsync(input, init)
        else -> {
            val options = makeJsCall<RequestInit>(
                jsObjectAssign(),
                makeJsObject<RequestInit>(),
                init,
                config.nodeOptions,
            )
            fetchAsync(input, options)
        }
    }

    promise.then(
        onFulfilled = { x: JsAny ->
            continuation.resume(x.unsafeCast())
            null
        },
        onRejected = { it: JsErrorLike? ->
            continuation.resumeWithException(Error("Fail to fetch", JsError(it)))
            null
        }
    )
}

private fun AbortController(): AbortController {
    val ctor = abortControllerCtorBrowser()
    return makeJsNew(ctor)
}

private fun abortControllerCtorBrowser(): AbortController = js("AbortController")

internal fun CoroutineScope.readBody(response: Response): ByteReadChannel =
    readBodyBrowser(response)
