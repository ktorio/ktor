package io.ktor.client

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.*
import java.nio.charset.*


private val DEFAULT_RESPONSE_POOL_SIZE = 1000

private val ResponsePool = object : DefaultPool<ByteBuffer>(DEFAULT_RESPONSE_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(8192)!!
}

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
    val result = contentLength()?.let { ByteArrayOutputStream(it) } ?: ByteArrayOutputStream()
    val buffer = ResponsePool.borrow()
    val channel = bodyChannel

    while (true) {
        buffer.clear()
        val count = channel.read(buffer)
        if (count == -1) break
        buffer.flip()

        result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count)
    }

    ResponsePool.recycle(buffer)
    return result.toByteArray()
}

suspend fun HttpResponse.discardRemaining() {
    val channel = bodyChannel
    val buffer = ResponsePool.borrow()

    while (true) {
        buffer.clear()
        if (channel.read(buffer) == -1) break
    }

    ResponsePool.recycle(buffer)
}

private object EmptyInputStream : InputStream() {
    override fun read(): Int = -1
}

val HttpResponse.bodyStream: InputStream
    get() = when (body) {
        is EmptyBody -> EmptyInputStream
        is InputStreamBody -> body.stream
        is OutputStreamBody -> ByteArrayOutputStream().apply(body.block).toByteArray().inputStream()
        else -> error("Body has been already processed by some feature: $body")
    }

val HttpResponse.bodyChannel: ReadChannel get() = bodyStream.toReadChannel()
