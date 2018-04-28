package io.ktor.client.response

import io.ktor.cio.*
import io.ktor.client.utils.*
import io.ktor.http.*

/**
 * Exactly reads [count] bytes of the [HttpResponse.content].
 */
suspend fun HttpResponse.readBytes(count: Int): ByteArray =
    ByteArray(count).also { content.readFully(it) }

/**
 * Reads the whole [HttpResponse.content] if Content-Length was specified.
 * Otherwise it just reads one byte.
 */
suspend fun HttpResponse.readBytes(): ByteArray =
    content.toByteArray(contentLength() ?: 1)

/**
 * Efficiently discards the remaining bytes of [HttpResponse.content].
 */
suspend fun HttpResponse.discardRemaining(): Unit = HttpClientDefaultPool.use { buffer ->
    content.pass(buffer) { it.clear() }
}
