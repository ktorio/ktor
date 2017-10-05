package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.request.*
import java.io.*

interface IncomingContent {
    val request: ApplicationRequest

    fun readChannel(): ReadChannel
    fun multiPartData(): MultiPartData
    fun inputStream(): InputStream = readChannel().toInputStream()
}

suspend fun IncomingContent.readText(): String {
    val buffer = ByteBufferWriteChannel()
    request.headers[HttpHeaders.ContentLength]?.toInt()?.let { contentLength ->
        buffer.ensureCapacity(contentLength)
    }

    readChannel().copyTo(buffer) // TODO provide buffer pool to copyTo function
    return buffer.toByteArray().toString(request.contentCharset() ?: Charsets.ISO_8859_1)
}
