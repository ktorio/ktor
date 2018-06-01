package io.ktor.client.response

import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*

/**
 * Exactly reads [count] bytes of the [HttpResponse.content].
 */
suspend fun HttpResponse.readBytes(count: Int): ByteArray =
    ByteArray(count).also { content.readFully(it) }

/**
 * Reads the whole [HttpResponse.content] if Content-Length was specified.
 * Otherwise it just reads one byte.
 */
suspend fun HttpResponse.readBytes(): ByteArray = content.readRemaining(Long.MAX_VALUE).readBytes()

/**
 * Efficiently discards the remaining bytes of [HttpResponse.content].
 */
suspend fun HttpResponse.discardRemaining() {
    content.discard()
}
