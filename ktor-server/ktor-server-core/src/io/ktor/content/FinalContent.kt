package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import kotlin.coroutines.experimental.*

/**
 * Information about the content to be sent to the peer, recognized by an [ApplicationHost]
 */
sealed class FinalContent {
    /**
     * Status code to set when sending this content
     */
    open val status: HttpStatusCode?
        get() = null

    /**
     * Headers to set when sending this content
     */
    open val headers: ValuesMap
        get() = ValuesMap.Empty

    /**
     * Variant of a [FinalContent] without payload
     */
    abstract class NoContent : FinalContent()

    /**
     * Variant of a [FinalContent] with payload read from [ReadChannel]
     *
     */
    abstract class ReadChannelContent : FinalContent() {
        /**
         * Provides [ReadChannel] from which host will read the data and send it to peer
         */
        abstract fun readFrom(): ReadChannel
    }

    /**
     * Variant of a [FinalContent] with payload written to [WriteChannel]
     */
    abstract class WriteChannelContent : FinalContent() {
        /**
         * Receives [channel] provided by the host and writes all data to it
         */
        abstract suspend fun writeTo(channel: WriteChannel)
    }

    /**
     * Variant of a [FinalContent] with payload represented as [ByteArray]
     */
    abstract class ByteArrayContent : FinalContent() {
        /**
         * Provides [ByteArray] which host will send to peer
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of a [FinalContent] for upgrading an HTTP connection
     */
    abstract class ProtocolUpgrade : FinalContent() {
        /**
         * Upgrades an HTTP connection
         * @param input is a [ReadChannel] for an upgraded connection
         * @param output is a [WriteChannel] for an upgraded connection
         * @param closeable is a [Closeable] instance to call when upgraded connection terminates
         * @param hostContext is a [CoroutineContext] to execute non-blocking code, such as parsing or processing
         * @param userAppContext is a [CoroutineContext] to execute user-provided callbacks or code potentially blocking
         */
        abstract suspend fun upgrade(input: ReadChannel,
                                     output: WriteChannel,
                                     closeable: Closeable,
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
