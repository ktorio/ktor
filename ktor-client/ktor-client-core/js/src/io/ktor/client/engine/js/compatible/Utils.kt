package io.ktor.client.engine.js.compatible

import io.ktor.client.engine.js.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.browser.*
import kotlin.coroutines.*
import kotlin.js.*

private val isBrowser: Boolean = js(
    "typeof window !== 'undefined' && typeof window.document !== 'undefined'"
) as Boolean

private val isNode: Boolean = js(
    "typeof process !== 'undefined' && process.versions != null && process.versions.node != null"
) as Boolean

internal suspend fun fetch(input: String, init: RequestInit): Response = if (isNode) {
        val nodeFetch: dynamic = jsRequire("node-fetch")
        nodeFetch(input, init) as Promise<Response>
    } else {
        window.fetch(input, init)
}.await()

internal fun readBody(
    response: Response,
    callContext: CoroutineContext
): ByteReadChannel = if (isNode) callContext.readBodyBlocking(response) else callContext.readBodyStream(response)

private fun CoroutineContext.readBodyBlocking(response: Response): ByteReadChannel = GlobalScope.writer(this) {
    val responseBuffer = response.arrayBuffer().await()
    channel.writeFully(Uint8Array(responseBuffer).asByteArray())
}.channel

private fun CoroutineContext.readBodyStream(response: Response): ByteReadChannel {
    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    val stream = response.body as? ReadableStream ?: error("Fail to obtain native stream: $response")
    return stream.toByteChannel(this)
}

private fun ReadableStream.toByteChannel(
    callContext: CoroutineContext
): ByteReadChannel = GlobalScope.writer(callContext) {
    val reader = getReader()
    while (true) {
        val chunk = reader.readChunk() ?: break
        channel.writeFully(chunk.asByteArray())
    }
}.channel

private fun jsRequire(moduleName: String): dynamic = try {
    js("require(moduleName)")
} catch (cause: dynamic) {
    throw Error("Error loading module '$moduleName': $cause")
}
