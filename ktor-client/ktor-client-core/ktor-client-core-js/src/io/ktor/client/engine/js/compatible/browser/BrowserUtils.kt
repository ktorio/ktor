package io.ktor.client.engine.js.compatible.browser

import io.ktor.client.engine.js.ReadableStream
import io.ktor.client.engine.js.compatible.Utils
import io.ktor.client.engine.js.toByteChannel
import kotlinx.coroutines.io.ByteReadChannel
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.browser.window
import kotlin.coroutines.CoroutineContext
import kotlin.js.Promise

object BrowserUtils : Utils() {
    override fun getBodyContentAsChannel(resp: Response, context: CoroutineContext): ByteReadChannel {
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val stream = resp.body as? ReadableStream ?: error("Fail to obtain native stream: $resp")
        return stream.toByteChannel(context)
    }

    override fun fetch(input: dynamic, init: RequestInit): Promise<Response> {
        return window.fetch(input, init)
    }
}
