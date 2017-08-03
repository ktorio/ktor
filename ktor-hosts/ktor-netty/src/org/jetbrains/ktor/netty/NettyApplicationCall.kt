package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.response.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCall(application: Application,
                                    private val context: ChannelHandlerContext,
                                    private val httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue,
                                    private val hostCoroutineContext: CoroutineContext,
                                    private val userCoroutineContext: CoroutineContext) : BaseApplicationCall(application) {

    private val closed = AtomicBoolean(false)

    override val request = NettyApplicationRequest(this, httpRequest, NettyConnectionPoint(httpRequest, context), contentQueue)
    override val response = NettyApplicationResponse(this, context)
    override val bufferPool = NettyByteBufferPool(context)

    init {
        sendPipeline.intercept(ApplicationSendPipeline.Host) {
            try {
                response.close()
            } finally {
                try {
                    request.close()
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

                    context.channel().attr(NettyHostHttp1Handler.ResponseQueueKey).get()?.completed(this)
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
        response.sendResponseMessage(flush = false, chunked = false)
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

            response.sendResponseMessage(chunked = false)?.addListener {
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

    override suspend fun responseChannel(): WriteChannel = response.responseChannel()
}