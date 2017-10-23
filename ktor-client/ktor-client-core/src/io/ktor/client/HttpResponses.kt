package io.ktor.client

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import java.io.*
import java.nio.*
import java.nio.charset.*


suspend fun HttpResponse.readText(): String = receive()

// TODO: support charset
suspend fun HttpResponse.readText(charset: Charset): String = receive()

suspend fun HttpResponse.readBytes(count: Int): ByteArray {
    val result = ByteArray(count)
    val buffer = ByteBuffer.wrap(result)
    val channel = bodyChannel

    while (buffer.hasRemaining()) {
        if (channel.read(buffer) < 0) error("Unexpected EOF, ${buffer.remaining()} remaining of $count")
    }

    return result
}

suspend fun HttpResponse.readBytes(): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteBuffer.allocate(8192)
    val channel = bodyChannel

    while (true) {
        buffer.clear()
        val count = channel.read(buffer)
        if (count == -1) break
        buffer.flip()

        result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count)
    }

    return result.toByteArray()
}

suspend fun HttpResponse.discardRemaining() {
    val channel = bodyChannel
    val buffer = ByteBuffer.allocate(8192)

    while (true) {
        buffer.clear()
        if (channel.read(buffer) == -1) break
    }
}

val HttpResponse.bodyStream: InputStream get() {
    if (body is InputStreamBody) return body.stream
    error("Body has been already processed by some feature: $body")
}

val HttpResponse.bodyChannel: ReadChannel get() = bodyStream.toReadChannel()
