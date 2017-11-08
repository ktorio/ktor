package io.ktor.client.utils

import kotlinx.coroutines.experimental.io.*


sealed class HttpMessageBody
class ByteReadChannelBody(val channel: ByteReadChannel) : HttpMessageBody()
class ByteWriteChannelBody(val block: suspend (ByteWriteChannel) -> Unit) : HttpMessageBody()

object EmptyBody : HttpMessageBody()
