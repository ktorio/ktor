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
import org.w3c.dom.events.*
import org.w3c.fetch.*
import kotlin.coroutines.*
import kotlin.js.Promise

internal suspend fun commonFetch(
    input: String,
    init: RequestInit
): Response = suspendCancellableCoroutine { continuation ->
    val controller = AbortController()
    init.signal = controller.signal

    continuation.invokeOnCancellation {
        controller.abort()
    }

    val promise: Promise<Response> = if (PlatformUtils.IS_BROWSER) {
        window.fetch(input, init)
    } else {
        jsRequireNodeFetch()(input, init) as Promise<Response>
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

internal fun AbortController(): AbortController {
    return if (PlatformUtils.IS_BROWSER) {
        js("new AbortController()") as AbortController
    } else {
        val controller = js("require('abort-controller')")
        js("new controller()") as AbortController
    }
}

internal fun CoroutineScope.readBody(
    response: Response
): ByteReadChannel = if (PlatformUtils.IS_BROWSER) {
    readBodyBrowser(response)
} else {
    readBodyNode(response)
}

private fun jsRequireNodeFetch(): dynamic = try {
    js("require('node-fetch')")
} catch (cause: dynamic) {
    throw Error("Error loading module 'node-fetch': $cause")
}

// https://youtrack.jetbrains.com/issue/KT-29243
/**
 * https://fetch.spec.whatwg.org/#dom-request-signal
 */
internal var RequestInit.signal: AbortSignal
    get() = asDynamic().signal as AbortSignal
    set(newValue) {
        asDynamic().signal = newValue
    }

/**
 * Exposes the JavaScript [AbortController](https://dom.spec.whatwg.org/#interface-abortcontroller) to Kotlin
 */
internal external interface AbortController {
    var signal: AbortSignal
    fun abort()
}

/**
 * Exposes the JavaScript [AbortSignal](https://dom.spec.whatwg.org/#interface-AbortSignal) to Kotlin
 */
internal abstract external class AbortSignal : EventTarget {
    var aborted: Boolean
    var onabort: ((AbortSignal, ev: Event) -> Any)?
        get() = definedExternally
        set(value) = definedExternally
}
