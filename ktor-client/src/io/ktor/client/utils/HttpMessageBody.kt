package io.ktor.client.utils

import io.ktor.cio.ReadChannel
import io.ktor.cio.WriteChannel


sealed class HttpMessageBody
class ReadChannelBody(val channel: ReadChannel) : HttpMessageBody()
class WriteChannelBody(val block: (WriteChannel) -> Unit) : HttpMessageBody()

object EmptyBody : HttpMessageBody()