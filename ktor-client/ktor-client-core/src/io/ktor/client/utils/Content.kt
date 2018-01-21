package io.ktor.client.utils

import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*


object EmptyContent : OutgoingContent.NoContent()

fun OutgoingContent.wrapHeaders(block: (Headers) -> Headers): OutgoingContent = when (this) {
    is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
        override val headers: Headers = block(this@wrapHeaders.headers)
    }
    is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun readFrom(): ByteReadChannel = this@wrapHeaders.readFrom()

        override fun readFrom(range: LongRange): ByteReadChannel = this@wrapHeaders.readFrom(range)
    }
    is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun writeTo(channel: ByteWriteChannel) = this@wrapHeaders.writeTo(channel)
    }
    is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
        override val headers: Headers = block(this@wrapHeaders.headers)

        override fun bytes(): ByteArray = this@wrapHeaders.bytes()
    }
    is OutgoingContent.ProtocolUpgrade -> object : OutgoingContent.ProtocolUpgrade() {
        override val headers: Headers = block(this@wrapHeaders.headers)

        override suspend fun upgrade(
                input: ByteReadChannel,
                output: ByteWriteChannel,
                engineContext: CoroutineContext,
                userContext: CoroutineContext
        ): Job = this@wrapHeaders.upgrade(input, output, engineContext, userContext)
    }
}
