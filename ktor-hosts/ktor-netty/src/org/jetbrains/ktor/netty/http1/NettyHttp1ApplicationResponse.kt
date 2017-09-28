package org.jetbrains.ktor.netty.http1

import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.netty.cio.*
import org.jetbrains.ktor.response.*
import java.io.*
import kotlin.coroutines.experimental.*

internal class NettyHttp1ApplicationResponse(call: NettyApplicationCall,
                                             context: ChannelHandlerContext,
                                             hostCoroutineContext: CoroutineContext,
                                             userCoroutineContext: CoroutineContext,
                                             val protocol: HttpVersion)

    : NettyApplicationResponse(call, context, hostCoroutineContext, userCoroutineContext) {

    private var responseStatus: HttpResponseStatus = HttpResponseStatus.OK
    private val responseHeaders = io.netty.handler.codec.http.DefaultHttpHeaders()

    override fun setStatus(statusCode: HttpStatusCode) {
        val cached = responseStatusCache[statusCode.value]

        responseStatus = cached?.takeIf { cached.reasonPhrase() == statusCode.description }
                ?: HttpResponseStatus(statusCode.value, statusCode.description)
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            responseHeaders.add(name, value)
        }

        override fun getHostHeaderNames(): List<String> = responseHeaders.map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = responseHeaders.getAll(name) ?: emptyList()
    }

    override fun responseMessage(chunked: Boolean, last: Boolean): Any {
        val responseMessage = DefaultHttpResponse(protocol, responseStatus, responseHeaders)
        if (chunked) {
            setChunked(responseMessage)
        }
        return responseMessage
    }

    override suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        val nettyContext = context
        val nettyChannel = nettyContext.channel()
        val userAppContext = userCoroutineContext + NettyDispatcher.CurrentContext(nettyContext)

        run(hostCoroutineContext) {
            val bodyHandler = context.pipeline().get(RequestBodyHandler::class.java)
            val upgradedReadChannel = bodyHandler.newChannel()
            val upgradedWriteChannel = ByteChannel()

            with(nettyChannel.pipeline()) {
                remove(NettyHostHttp1Handler::class.java)
                addFirst(NettyDirectDecoder())
            }

            sendResponse(chunked = false, content = upgradedWriteChannel)
            run(userAppContext) {
                upgrade.upgrade(CIOReadChannelAdapter(upgradedReadChannel), CIOWriteChannelAdapter(upgradedWriteChannel), Close(upgradedWriteChannel, bodyHandler), hostCoroutineContext, userAppContext)
            }

            (call as NettyApplicationCall).responseWriteJob.join()
        }
    }

    private class Close(private val bc: ByteWriteChannel, private val handler: RequestBodyHandler) : Closeable {
        override fun close() {
            bc.close()
            handler.close()
        }
    }

    private fun setChunked(message: HttpResponse) {
        if (message.status().code() != HttpStatusCode.SwitchingProtocols.value) {
            HttpUtil.setTransferEncodingChunked(message, true)
        }
    }
}