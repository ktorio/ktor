package io.ktor.client.response

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.nio.charset.*


suspend fun HttpResponse.readText(): String = call.receive()

suspend fun HttpResponse.readText(charset: Charset): String = readBytes().toString(charset)

suspend fun HttpResponse.readBytes(count: Int): ByteArray {
    val result = ByteArray(count)
    receiveContent().readChannel().readFully(result)
    return result
}

suspend fun HttpResponse.readBytes(): ByteArray {
    val content = receiveContent()
    val sizeHint = content.contentLength() ?: 0
    return content.readChannel().toByteArray(sizeHint)
}

suspend fun HttpResponse.discardRemaining() = HttpClientDefaultPool.use { buffer ->
    receiveContent().readChannel().pass(buffer) {
        it.clear()
    }
}
