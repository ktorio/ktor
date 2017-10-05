package io.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.request.*
import io.ktor.util.*
import java.io.*
import java.util.concurrent.atomic.*

internal class NettyApplicationRequest(
        call: ApplicationCall,
        private val httpRequest: HttpRequest,
        context: ChannelHandlerContext,
        private val contentQueue: NettyContentQueue) : BaseApplicationRequest(call) {

    override val local = NettyConnectionPoint(httpRequest, context)

    override val queryParameters = object : ValuesMap {
        private val decoder = QueryStringDecoder(httpRequest.uri())
        override val caseInsensitiveKey: Boolean get() = true
        override fun getAll(name: String) = decoder.parameters()[name]
        override fun names() = decoder.parameters().keys
        override fun entries() = decoder.parameters().entries
        override fun isEmpty() = decoder.parameters().isEmpty()
    }

    override val headers: ValuesMap = NettyApplicationRequestHeaders(httpRequest)
    override val cookies: RequestCookies = NettyApplicationRequestCookies(this)

    override fun receiveContent() = NettyHttpIncomingContent(this)


    private val contentChannelState = AtomicReference<ReadChannelState>(ReadChannelState.NEUTRAL)
    private val contentChannel = lazy { NettyHttp1ReadChannel(contentQueue) }
    private val contentMultipart = lazy {
        if (!isMultipart())
            throw IOException("The request content is not multipart encoded")
        val decoder = HttpPostMultipartRequestDecoder(httpRequest)
        NettyMultiPartData(decoder, contentQueue)
    }

    fun close() {
        if (contentMultipart.isInitialized()) {
            contentMultipart.value.destroy()
        }

        if (contentChannel.isInitialized()) {
            contentChannel.value.close()
        }
    }

    private enum class ReadChannelState {
        NEUTRAL,
        RAW_CHANNEL,
        MULTIPART_HANDLER
    }

    class NettyHttpIncomingContent internal constructor(override val request: NettyApplicationRequest) : IncomingContent {
        private fun AtomicReference<NettyApplicationRequest.ReadChannelState>.switchTo(newState: NettyApplicationRequest.ReadChannelState) =
                get() == newState || compareAndSet(NettyApplicationRequest.ReadChannelState.NEUTRAL, newState)

        override fun readChannel(): ReadChannel {
            if (request.contentChannelState.switchTo(NettyApplicationRequest.ReadChannelState.RAW_CHANNEL)) {
                return request.contentChannel.value
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
}

