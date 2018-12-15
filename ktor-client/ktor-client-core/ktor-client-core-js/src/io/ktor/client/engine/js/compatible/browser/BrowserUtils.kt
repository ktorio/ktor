package io.ktor.client.engine.js.compatible.browser

import io.ktor.client.engine.js.*
import io.ktor.client.engine.js.compatible.*
import kotlinx.coroutines.io.*
import org.w3c.fetch.*
import kotlin.browser.*
import kotlin.coroutines.*
import kotlin.js.*

object BrowserUtils : Utils() {
    override fun getBodyContentAsChannel(resp: Response, context: CoroutineContext): ByteReadChannel {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val stream = resp.body as? ReadableStream ?: error("Fail to obtain native stream: $resp")
        return stream.toByteChannel(context)
    }

    override fun fetch(input: String, init: RequestInit): Promise<Response> {
        return window.fetch(input, init)
    }

    internal fun ReadableStream.toByteChannel(
        callContext: CoroutineContext
    ): ByteReadChannel = writer(callContext) {
        val reader = getReader()
        while (true) {
            val chunk = reader.readChunk() ?: break
            channel.writeFully(chunk.asByteArray())
        }
    }.channel
}
