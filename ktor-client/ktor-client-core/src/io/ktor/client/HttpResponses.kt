package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.charset.*

private object EmptyInputStream : InputStream() {
    override fun read(): Int = -1
}

val HttpResponse.bodyStream: InputStream
    get() = when (body) {
        is EmptyBody -> EmptyInputStream
        is ByteReadChannelBody,
        is ByteWriteChannelBody -> bodyChannel.toInputStream()
        else -> throw IllegalBodyStateException(body)
    }

val HttpResponse.bodyChannel: ByteReadChannel
    get() {
        val body = (body as? HttpMessageBody) ?: throw IllegalBodyStateException(body)
        return body.toByteReadChannel()
    }

suspend fun HttpResponse.readText(): String = receive()

suspend fun HttpResponse.readText(charset: Charset): String = receive()

suspend fun HttpResponse.readBytes(count: Int): ByteArray =
        (body as? HttpMessageBody)?.readBytes(count) ?: throw IllegalBodyStateException(body)

suspend fun HttpResponse.readBytes(): ByteArray {
    val body = (body as? HttpMessageBody) ?: throw IllegalBodyStateException(body)
    val sizeHint = contentLength() ?: DEFAULT_RESPONSE_SIZE
    return body.toByteArray(sizeHint)
}

suspend fun HttpMessageBody.discardRemaining() {
    val channel = toByteReadChannel()
    val buffer = HTTP_CLIENT_RESPONSE_POOL.borrow()

    while (true) {
        buffer.clear()
        if (channel.readAvailable(buffer) == -1) break
    }

    HTTP_CLIENT_RESPONSE_POOL.recycle(buffer)
}

suspend fun HttpResponse.discardRemaining() = (body as? HttpMessageBody)?.discardRemaining()

class IllegalBodyStateException(val body: Any) : IllegalStateException("Body has been already processed by some feature: $body")