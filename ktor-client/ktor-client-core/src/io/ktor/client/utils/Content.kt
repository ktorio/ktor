package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

/**
 * Concrete [OutgoingContent] without a payload.
 */
object EmptyContent : OutgoingContent.NoContent()

/**
 * Generates a new [OutgoingContent] of the same abstract type
 * but with [OutgoingContent.headers] transformed by the specified [block].
 */
fun OutgoingContent.wrapHeaders(block: (Headers) -> Headers): OutgoingContent = when (this) {
    is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)
    }
    is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun readFrom(): ByteReadChannel = this@wrapHeaders.readFrom()

        override fun readFrom(range: LongRange): ByteReadChannel = this@wrapHeaders.readFrom(range)
    }
    is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun writeTo(channel: ByteWriteChannel) = this@wrapHeaders.writeTo(channel)
    }
    is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType
        override val status: HttpStatusCode? get() = this@wrapHeaders.status

        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun bytes(): ByteArray = this@wrapHeaders.bytes()
    }
    is OutgoingContent.ProtocolUpgrade -> object : OutgoingContent.ProtocolUpgrade() {
        override val contentLength: Long? get() = this@wrapHeaders.contentLength
        override val contentType: ContentType? get() = this@wrapHeaders.contentType

        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun upgrade(
            input: ByteReadChannel,
            output: ByteWriteChannel,
            engineContext: CoroutineContext,
            userContext: CoroutineContext
        ): Job = this@wrapHeaders.upgrade(input, output, engineContext, userContext)
    }
}
