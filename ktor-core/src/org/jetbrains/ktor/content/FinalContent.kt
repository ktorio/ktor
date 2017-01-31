package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.io.*

interface HostResponse {
    val status: HttpStatusCode?
        get() = null

    val headers: ValuesMap
}

sealed class FinalContent : HostResponse {
    abstract class NoContent : FinalContent()

    abstract class ReadChannelContent : FinalContent() {
        abstract fun readFrom(): ReadChannel
    }

    abstract class WriteChannelContent : FinalContent() {
        abstract suspend fun writeTo(channel: WriteChannel)
    }

    abstract class ByteArrayContent : FinalContent() {
        abstract fun bytes(): ByteArray
    }
}

fun FinalContent.contentLength(): Long? {
    if (this is Resource) {
        return contentLength
    }

    return headers[HttpHeaders.ContentLength]?.let(String::toLong)
}

fun FinalContent.contentType(): ContentType? {
    if (this is Resource) {
        return contentType
    }

    return headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
}
