package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.util.concurrent.atomic.*

internal abstract class NettyApplicationRequest(
        call: ApplicationCall,
        protected val context: ChannelHandlerContext,
        private val requestBodyChannel: ByteReadChannel,
        protected val uri: String,
        internal val keepAlive: Boolean) : BaseApplicationRequest(call) {

    final override val queryParameters = object : ValuesMap {
        private val decoder = QueryStringDecoder(uri)
        override val caseInsensitiveName: Boolean get() = true
        override fun getAll(name: String) = decoder.parameters()[name]
        override fun names() = decoder.parameters().keys
        override fun entries() = decoder.parameters().entries
        override fun isEmpty() = decoder.parameters().isEmpty()
    }

    override val cookies: RequestCookies = NettyApplicationRequestCookies(this)

    override fun receiveContent() = NettyHttpIncomingContent(this)

    internal val contentChannelState = AtomicReference<ReadChannelState>(ReadChannelState.NEUTRAL)
    internal val contentChannel = requestBodyChannel
    internal val contentMultipart = lazy {
        if (!isMultipart())
            throw IOException("The request content is not multipart encoded")
        val decoder = newDecoder()
        NettyMultiPartData(decoder, context.alloc(), requestBodyChannel)
    }

    protected abstract fun newDecoder(): HttpPostMultipartRequestDecoder

    final fun close() {
        if (contentMultipart.isInitialized()) {
            contentMultipart.value.destroy()
        }
    }

    internal enum class ReadChannelState {
        NEUTRAL,
        RAW_CHANNEL,
        MULTIPART_HANDLER
    }
}
