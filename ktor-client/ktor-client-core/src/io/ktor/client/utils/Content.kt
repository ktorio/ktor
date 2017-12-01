package io.ktor.client.utils

import io.ktor.client.call.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*


object EmptyContent : OutgoingContent.NoContent()

fun OutgoingContent.wrapCookies(block: (Headers) -> Headers): OutgoingContent = when (this) {
    is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
        override val headers: ValuesMap = block(this@wrapCookies.headers)
    }
    is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
        override val headers: ValuesMap = block(this@wrapCookies.headers)

        override fun readFrom(): ByteReadChannel = this@wrapCookies.readFrom()

        override fun readFrom(range: LongRange): ByteReadChannel = this@wrapCookies.readFrom(range)
    }
    is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
        override val headers: ValuesMap = block(this@wrapCookies.headers)

        suspend override fun writeTo(channel: ByteWriteChannel) = this@wrapCookies.writeTo(channel)
    }
    is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
        override val headers: ValuesMap = block(this@wrapCookies.headers)

        override fun bytes(): ByteArray = this@wrapCookies.bytes()
    }
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
}
