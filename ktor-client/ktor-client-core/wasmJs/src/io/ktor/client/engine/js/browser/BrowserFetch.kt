/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.browser

import io.ktor.client.engine.js.*
import io.ktor.client.fetch.*
import io.ktor.client.utils.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.w3c.fetch.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal fun CoroutineScope.readBodyBrowser(response: Response): ByteReadChannel {
    val stream: ReadableStream<Uint8Array?> = response.body?.unsafeCast() ?: return ByteReadChannel.Empty
    return channelFromStream(stream)
}

internal fun CoroutineScope.channelFromStream(
    stream: ReadableStream<Uint8Array?>
): ByteReadChannel = writer {
    val reader: ReadableStreamDefaultReader<Uint8Array?> = stream.getReader()
    try {
        while (true) {
            val chunk = reader.readChunk() ?: break
            channel.writeFully(chunk.asByteArray())
            channel.flush()
        }
    } catch (cause: Throwable) {
        reader.cancel(cause.toJsReference()).catch { null }.await<Unit>()
        throw cause
    }
}.channel

internal suspend fun ReadableStreamDefaultReader<Uint8Array?>.readChunk(): Uint8Array? =
    suspendCoroutine { continuation ->
        read().then { stream: ReadableStreamReadResult<Uint8Array?> ->
            val chunk = stream.value
            val result = if (stream.done || chunk == null) null else chunk
            continuation.resume(result)
            null
        }.catch { cause: JsAny ->
            continuation.resumeWithException(JsError(cause))
            null
        }
    }
