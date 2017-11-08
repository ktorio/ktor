package io.ktor.client.utils

import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.charset.*


suspend fun HttpMessageBody.readBytes(count: Int): ByteArray {
    val result = ByteArray(count)
    toByteReadChannel().readFully(result)
    return result
}

suspend fun HttpMessageBody.toByteArray(sizeHint: Int = DEFAULT_RESPONSE_SIZE): ByteArray {
    val result = ByteArrayOutputStream(sizeHint)
    val buffer = HTTP_CLIENT_RESPONSE_POOL.borrow()
    val channel = toByteReadChannel()

    while (true) {
        buffer.clear()
        val count = channel.readAvailable(buffer)
        if (count == -1) break
        buffer.flip()

        result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), count)
    }

    HTTP_CLIENT_RESPONSE_POOL.recycle(buffer)
    return result.toByteArray()
}

suspend fun ByteWriteChannel.write(string: String, charset: Charset = Charsets.UTF_8) {
    writeFully(string.toByteArray(charset))
}

fun ByteWriteChannel.bufferedWriter(charset: Charset = Charsets.UTF_8): BufferedWriter = ByteWriteChannelOutputStream(this).bufferedWriter(charset)

fun ByteWriteChannel.writer(charset: Charset = Charsets.UTF_8): Writer = ByteWriteChannelOutputStream(this).writer(charset)
