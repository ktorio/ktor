package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.response.*
import java.io.Closeable
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationResponse(call: NettyApplicationCall,
                                        private val httpRequest: HttpRequest,
                                        private val context: ChannelHandlerContext,
                                        private val hostCoroutineContext: CoroutineContext,
                                        private val userCoroutineContext: CoroutineContext) : BaseApplicationResponse(call) {
    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    @Volatile
    private var responseMessageSent = false

    @Volatile
    private var responseChannel0: HttpContentWriteChannel? = null

    private val closed = AtomicBoolean(false)
    init {
        pipeline.intercept(ApplicationSendPipeline.Host) {
            try {
                close()
            } finally {
                try {
                    call.request.close()
                } finally {
                    if (closed.compareAndSet(false, true)) {
                        finalizeConnection()
                    }
                }
            }
        }
    }

    private fun finalizeConnection() {
        try {
            val finishContent = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            if (!HttpUtil.isKeepAlive(httpRequest)) {
                // close channel if keep-alive was not requested
                finishContent.addListener(ChannelFutureListener.CLOSE)
            } else {
                // reenable read operations on a channel if keep-alive was requested
                finishContent.addListener {
                    context.channel().config().isAutoRead = true
                    context.read()

                    context.channel().attr(NettyHostHttp1Handler.ResponseQueueKey).get()?.completed(call)
                    // resume next sendResponseMessage if queued
                }
            }
        } finally {
            ReferenceCountUtil.release(httpRequest)
        }
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        // Note that it shouldn't set HttpHeaders.ContentLength even if we know it here,
        // because it should've been set by commitHeaders earlier
        sendResponseMessage(flush = false, chunked = false)
        val buf = context.alloc().ioBuffer(bytes.size).writeBytes(bytes)
        context.writeAndFlush(buf).suspendAwait()
    }

    override suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        val nettyContext = context
        val nettyChannel = nettyContext.channel()
        val userAppContext = userCoroutineContext + NettyDispatcher.CurrentContext(nettyContext)

        run(hostCoroutineContext) {
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

                    upgrade.upgrade(HttpContentReadChannel(upgradeContentQueue.queue, buffered = false), responseChannel(), Closeable {
                        nettyChannel.close().get()
                        upgradeContentQueue.close()
                    }, hostCoroutineContext, userAppContext)
                    nettyContext.read()
                }
            } ?: throw IllegalStateException("Response has been already sent")
        }
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        val cached = responseStatusCache[statusCode.value]

        response.status = cached?.takeIf { cached.reasonPhrase() == statusCode.description }
                ?: HttpResponseStatus(statusCode.value, statusCode.description)
    }

    override suspend fun responseChannel(): WriteChannel {
        sendResponseMessage()

        return if (responseChannel0 == null) {
            val ch = HttpContentWriteChannel(context)
            responseChannel0 = ch
            ch
        } else responseChannel0!!
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            response.headers().add(name, value)
        }

        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    internal suspend fun sendResponseMessage(chunked: Boolean = true, flush: Boolean = true): ChannelFuture? {
        if (!responseMessageSent) {
            if (chunked)
                setChunked()

            context.channel().attr(NettyHostHttp1Handler.ResponseQueueKey).get()?.await(call)

            // TODO await for response queue
            val f = if (flush) context.writeAndFlush(response) else context.write(response)
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
            if (!response.headers().contains(HttpHeaders.TransferEncoding, HttpHeaderValues.CHUNKED, true)) {
                throw IllegalStateException("Already committed")
            }
        }
        if (response.status().code() != HttpStatusCode.SwitchingProtocols.value) {
            HttpUtil.setTransferEncodingChunked(response, true)
        }
    }

    companion object {
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}