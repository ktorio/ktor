package io.ktor.client.response

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.nio.charset.*


suspend fun HttpResponse.readBytes(count: Int): ByteArray =
        ByteArray(count).also { content.readFully(it) }

suspend fun HttpResponse.readBytes(): ByteArray =
        content.toByteArray(contentLength() ?: 1)

suspend fun HttpResponse.discardRemaining() = HttpClientDefaultPool.use { buffer ->
    content.pass(buffer) { it.clear() }
}
