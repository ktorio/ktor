package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.charset.*

interface IncomingContent : HttpMessage {
    fun readChannel(): ByteReadChannel
    fun multiPartData(): MultiPartData
    fun inputStream(): InputStream = readChannel().toInputStream()
}

suspend fun IncomingContent.readText(
        pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
        charset: Charset? = null
): String {
    val length = headers[HttpHeaders.ContentLength]?.toInt() ?: 1
    return readChannel().toByteArray(length, pool).toString(charset ?: charset() ?: Charsets.ISO_8859_1)
}
