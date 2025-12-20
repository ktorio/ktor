/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.browser

import io.ktor.client.engine.js.JsError
import io.ktor.client.utils.asByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import js.buffer.ArrayBuffer
import js.errors.JsErrorLike
import js.errors.toJsErrorLike
import js.errors.toJsErrorOrNull
import js.promise.await
import js.promise.catch
import js.typedarrays.Uint8Array
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import web.http.Response
import web.streams.ReadableStream
import web.streams.ReadableStreamDefaultReader
import web.streams.ReadableStreamReadResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.unsafeCast

internal fun CoroutineScope.readBodyBrowser(response: Response): ByteReadChannel {
    val stream: ReadableStream<Uint8Array<ArrayBuffer>?> = response.body?.unsafeCast() ?: return ByteReadChannel.Empty
    return channelFromStream(stream)
}

internal fun CoroutineScope.channelFromStream(
    stream: ReadableStream<Uint8Array<ArrayBuffer>?>
): ByteReadChannel = writer {
    val reader: ReadableStreamDefaultReader<Uint8Array<ArrayBuffer>?> = stream.getReader()
    try {
        while (true) {
            val chunk = reader.readChunk() ?: break
            channel.writeFully(chunk.asByteArray())
            channel.flush()
        }
    } catch (cause: Throwable) {
        reader.cancelAsync(cause.toJsErrorLike().toJsErrorOrNull())
            .catch { null }
            .await<Unit>()
        throw cause
    }
}.channel

internal suspend fun ReadableStreamDefaultReader<Uint8Array<ArrayBuffer>?>.readChunk(): Uint8Array<ArrayBuffer>? =
    suspendCancellableCoroutine { continuation ->
        readAsync().then { stream: ReadableStreamReadResult<Uint8Array<ArrayBuffer>?> ->
            val chunk = stream.value
            val result = if (stream.done || chunk == null) null else chunk
            continuation.resume(result)
            null
        }.catch { cause: JsErrorLike? ->
            continuation.resumeWithException(JsError(cause))
            null
        }
    }
