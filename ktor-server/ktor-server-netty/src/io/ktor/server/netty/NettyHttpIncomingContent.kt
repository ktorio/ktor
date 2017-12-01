package io.ktor.server.netty

import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.atomic.*

internal class NettyHttpIncomingContent internal constructor(
        val request: NettyApplicationRequest
) : IncomingContent {

    override val headers: Headers = request.headers

    private fun AtomicReference<NettyApplicationRequest.ReadChannelState>.switchTo(newState: NettyApplicationRequest.ReadChannelState) =
            get() == newState || compareAndSet(NettyApplicationRequest.ReadChannelState.NEUTRAL, newState)

    override fun readChannel(): ByteReadChannel {
        if (request.contentChannelState.switchTo(NettyApplicationRequest.ReadChannelState.RAW_CHANNEL)) {
            return request.contentChannel
        }

        throw IllegalStateException("Couldn't get channel, most likely multipart processing was already started, state is ${request.contentChannelState.get()}")
    }

    override fun multiPartData(): MultiPartData {
        if (request.contentChannelState.switchTo(NettyApplicationRequest.ReadChannelState.MULTIPART_HANDLER)) {
            return request.contentMultipart.value
        }

        throw IllegalStateException("Couldn't get multipart, most likely a raw channel already acquired, state is ${request.contentChannelState.get()}")
    }
}