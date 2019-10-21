/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.browser

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.ReadableStream
import io.ktor.client.fetch.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal fun CoroutineScope.readBodyBrowser(response: Response): ByteReadChannel {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val stream = response.body as? ReadableStream ?: error("Fail to obtain native stream: ${response.asDynamic()}")
    return channelFromStream(stream)
}

internal fun CoroutineScope.channelFromStream(
    stream: ReadableStream
): ByteReadChannel = writer {
    val reader = stream.getReader()
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
