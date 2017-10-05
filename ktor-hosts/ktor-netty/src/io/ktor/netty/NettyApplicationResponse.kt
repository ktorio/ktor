package io.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.response.*
import java.io.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationResponse(call: NettyApplicationCall,
                                        private val context: ChannelHandlerContext,
                                        private val hostCoroutineContext: CoroutineContext,
                                        private val userCoroutineContext: CoroutineContext) : BaseApplicationResponse(call) {

    private val responseMessage = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    @Volatile
    private var responseMessageSent = false

    @Volatile
    private var responseChannel0: NettyHttp1WriteChannel? = null

    suspend override fun respondFromBytes(bytes: ByteArray) {
        // Note that it shouldn't set HttpHeaders.ContentLength even if we know it here,
        // because it should've been set by commitHeaders earlier
        sendResponseMessage(flush = false, chunked = false)
        val buf = context.alloc().ioBuffer(bytes.size).writeBytes(bytes)
        context.writeAndFlush(buf).suspendWriteAwait()
    }

    override suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        val nettyContext = context
        val nettyChannel = nettyContext.channel()
        val userAppContext = userCoroutineContext + NettyDispatcher.CurrentContext(nettyContext)

        return run(hostCoroutineContext) {
            val upgradeContentQueue = RawContentQueue(nettyContext)

            nettyChannel.pipeline().replace(HttpContentQueue::class.java, "WebSocketReadQueue", upgradeContentQueue).popAndForEach {
                it.clear {
                    if (it is LastHttpContent)
                        it.release()
                    else
                        upgradeContentQueue.queue.push(it, false)
                }
            }

            with(nettyChannel.pipeline()) {
                remove(NettyHostHttp1Handler::class.java)
                addFirst(NettyDirectDecoder())
            }

            sendResponseMessage(chunked = false)?.addListener {
                launch(userAppContext) {
                    nettyChannel.pipeline().remove(HttpServerCodec::class.java)
                    nettyChannel.pipeline().addFirst(NettyDirectEncoder())

                    upgrade.upgrade(NettyHttp1ReadChannel(upgradeContentQueue.queue, buffered = false), responseChannel(), Closeable {
                        nettyChannel.close().get()
                        upgradeContentQueue.close()
                    }, hostCoroutineContext, userAppContext)
                    nettyContext.read()
                }
            } ?: throw IllegalStateException("Response has been already sent")
            Unit
        }
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        val cached = responseStatusCache[statusCode.value]

        responseMessage.status = cached?.takeIf { cached.reasonPhrase() == statusCode.description }
                ?: HttpResponseStatus(statusCode.value, statusCode.description)
    }

    override suspend fun responseChannel(): WriteChannel {
        sendResponseMessage()

        return if (responseChannel0 == null) {
            val ch = NettyHttp1WriteChannel(context)
            responseChannel0 = ch
            ch
        } else responseChannel0!!
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            responseMessage.headers().add(name, value)
        }

        override fun getHostHeaderNames(): List<String> = responseMessage.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = responseMessage.headers().getAll(name) ?: emptyList()
    }

    private suspend fun sendResponseMessage(chunked: Boolean = true, flush: Boolean = true): ChannelFuture? {
        if (!responseMessageSent) {
            if (chunked)
                setChunked()

            context.channel().attr(NettyHostHttp1Handler.ResponseQueueKey).get()?.await(call)

            // TODO await for response queue
            val f = if (flush) context.writeAndFlush(responseMessage) else context.write(responseMessage)
            responseMessageSent = true
            return f
        }

        return null
    }

    suspend fun close() {
        sendResponseMessage() // we don't need to suspendAwait() here as it handled in NettyApplicationCall
        responseChannel0?.close() // while close only does flush() and doesn't terminate connection
    }

    private fun setChunked() {
        if (responseMessageSent) {
            if (!responseMessage.headers().contains(HttpHeaders.TransferEncoding, HttpHeaderValues.CHUNKED, true)) {
                throw IllegalStateException("Already committed")
            }
        }
        if (responseMessage.status().code() != HttpStatusCode.SwitchingProtocols.value) {
            HttpUtil.setTransferEncodingChunked(responseMessage, true)
        }
    }

    companion object {
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}