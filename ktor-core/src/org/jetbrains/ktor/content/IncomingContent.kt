package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import java.io.*

interface IncomingContent {
    val request : ApplicationRequest

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
