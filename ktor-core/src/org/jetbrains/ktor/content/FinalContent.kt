package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*

interface HostResponse {
    val status: HttpStatusCode?
        get() = null

    val headers: ValuesMap
}

sealed class FinalContent : HostResponse {
    abstract class NoContent : FinalContent()

    abstract class ChannelContent : FinalContent() {
        abstract fun channel(): ReadChannel
    }

    abstract class StreamContentProvider : FinalContent() {
        abstract fun stream(): InputStream
    }

}

abstract class StreamConsumer : HostResponse {
    abstract fun stream(out : OutputStream): Unit
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
