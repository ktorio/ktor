package io.ktor.client.response

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.nio.charset.*


suspend fun BaseHttpResponse.readText(): String = call.receive()

suspend fun BaseHttpResponse.readText(charset: Charset): String = readBytes().toString(charset)

suspend fun BaseHttpResponse.readBytes(count: Int): ByteArray {
    val result = ByteArray(count)
    receiveContent().readChannel().readFully(result)
    return result
}

suspend fun BaseHttpResponse.readBytes(): ByteArray {
    val content = receiveContent()
    val sizeHint = content.contentLength() ?: 0
    return content.readChannel().toByteArray(sizeHint)
}

suspend fun BaseHttpResponse.discardRemaining() = HttpClientDefaultPool.use { buffer ->
    receiveContent().readChannel().pass(buffer) {
        it.clear()
    }
}
