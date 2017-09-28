package org.jetbrains.ktor.netty

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.request.*
import java.util.concurrent.atomic.*

internal class NettyHttpIncomingContent internal constructor(override val request: NettyApplicationRequest) : IncomingContent {
    private fun AtomicReference<NettyApplicationRequest.ReadChannelState>.switchTo(newState: NettyApplicationRequest.ReadChannelState) =
            get() == newState || compareAndSet(NettyApplicationRequest.ReadChannelState.NEUTRAL, newState)

    override fun readChannel(): ReadChannel {
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