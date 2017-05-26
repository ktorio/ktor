package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.coroutines.experimental.*

sealed class FinalContent {
    open val status: HttpStatusCode?
        get() = null

    open val headers: ValuesMap
        get() = ValuesMap.Empty

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

    abstract class ProtocolUpgrade : FinalContent() {
        abstract suspend fun upgrade(call: ApplicationCall,
                                     input: ReadChannel,
                                     output: WriteChannel,
                                     channel: Closeable,
                                     hostContext: CoroutineContext,
                                     userAppContext: CoroutineContext): Closeable
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
