/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatible

import io.ktor.client.engine.js.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.browser.*
import kotlin.coroutines.*
import kotlin.js.*

internal suspend fun fetch(input: String, init: RequestInit): Response = if (PlatformUtils.IS_NODE) {
    val nodeFetch: dynamic = jsRequire("node-fetch")
    nodeFetch(input, init) as Promise<Response>
} else {
    window.fetch(input, init)
}.await()

internal fun readBody(
    response: Response,
    callContext: CoroutineContext
): ByteReadChannel = if (PlatformUtils.IS_NODE) {
    callContext.readBodyNode(response)
} else {
    callContext.readBodyBrowser(response)
}

private fun CoroutineContext.readBodyNode(response: Response): ByteReadChannel = GlobalScope.writer(this) {
    val body = response.body ?: error("Fail to get body")
    val responseData = Channel<ByteArray>(Channel.UNLIMITED)

    body.on("data") { chunk: ArrayBuffer ->
        responseData.offer(Uint8Array(chunk).asByteArray())
    }

    body.on("error") { error ->
        val cause = JsError(error)
        responseData.close(cause)
        channel.close(cause)
    }

    body.on("end") {
        responseData.close()
    }

    try {
        for (chunk in responseData) {
            channel.writeFully(chunk)
        }
    } catch (cause: Throwable) {
        body.destroy(cause)
        throw cause
    }

    Unit
}.channel

private fun CoroutineContext.readBodyBrowser(response: Response): ByteReadChannel {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val stream = response.body as? ReadableStream ?: error("Fail to obtain native stream: ${response.asDynamic()}")
    return stream.toByteChannel(this)
}

private fun ReadableStream.toByteChannel(
    callContext: CoroutineContext
): ByteReadChannel = GlobalScope.writer(callContext) {
    val reader = getReader()
    while (true) {
        try {
            val chunk = reader.readChunk() ?: break
            channel.writeFully(chunk.asByteArray())
        } catch (cause: Throwable) {
            reader.cancel(cause)
            throw cause
        }
    }
}.channel

private fun jsRequire(moduleName: String): dynamic = try {
    js("require(moduleName)")
} catch (cause: dynamic) {
    throw Error("Error loading module '$moduleName': $cause")
}
