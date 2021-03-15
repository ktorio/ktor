/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.js.compatibility.*
import io.ktor.utils.io.*
import kotlinx.browser.*
import kotlinx.coroutines.*
import org.w3c.dom.*
import org.w3c.fetch.*
import kotlin.js.Promise

/**
 * Browser JS API wrapper
 */
public class Browser(private val customFetchSettings: RequestInit.() -> Unit = { }) : JsPlatformApi {

    override fun fetch(url: String): Promise<Response> = window.fetch(url)

    override fun fetch(url: String, init: RequestInit): Promise<Response> = window.fetch(url, init.apply(customFetchSettings))

    override fun createAbortController(): AbortController = AbortController()

    override fun createHeaders(): Headers = Headers()

    override fun createWebSocket(urlString: String): WebSocket = WebSocket(urlString)

    override fun readBody(scope: CoroutineScope, response: Response): ByteReadChannel {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val stream = response.body as? ReadableStream ?: error("Fail to obtain native stream: ${response.asDynamic()}")
        return scope.channelFromStream(stream)
    }
}

private fun CoroutineScope.channelFromStream(
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
