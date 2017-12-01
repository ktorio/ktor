package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*

interface IncomingContent : HttpMessage {
    fun readChannel(): ByteReadChannel
    fun multiPartData(): MultiPartData
    fun inputStream(): InputStream = readChannel().toInputStream()
}

suspend fun IncomingContent.readText(pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool): String {
    val length = headers[HttpHeaders.ContentLength]?.toInt() ?: 1
    return readChannel().toByteArray(length, pool).toString(charset() ?: Charsets.ISO_8859_1)
}
