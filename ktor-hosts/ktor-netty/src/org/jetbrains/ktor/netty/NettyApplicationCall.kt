package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import java.io.*
import java.util.concurrent.atomic.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue) : BaseApplicationCall(application) {

    var completed: Boolean = false

    private val closed = AtomicBoolean(false)

    override val request = NettyApplicationRequest(this, httpRequest, NettyConnectionPoint(httpRequest, context), contentQueue)
    override val response = NettyApplicationResponse(this, context)
    override val bufferPool = NettyByteBufferPool(context)

    suspend override fun respond(message: Any) {
        super.respond(message)

        completed = true
        response.close()
        request.close()

        if (closed.compareAndSet(false, true)) {
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
        runAsync(context.channel().eventLoop()) {
            val upgradeContentQueue = RawContentQueue(context)

            context.channel().pipeline().replace(HttpContentQueue::class.java, "WebSocketReadQueue", upgradeContentQueue).popAndForEach {
                it.clear {
                    if (it is LastHttpContent)
                        it.release()
                    else
                        upgradeContentQueue.queue.push(it, false)
                }
            }

            with(context.channel().pipeline()) {
                remove(NettyHostHttp1Handler::class.java)
                addFirst(NettyDirectDecoder())
            }

            response.sendResponseMessage(chunked = false)?.addListener {
                launchAsync(context.channel().eventLoop()) {
                    context.channel().pipeline().remove(HttpServerCodec::class.java)
                    context.channel().pipeline().addFirst(NettyDirectEncoder())

                    upgrade.upgrade(this@NettyApplicationCall, HttpContentReadChannel(upgradeContentQueue.queue, buffered = false), responseChannel(), Closeable {
                        context.channel().close().get()
                        upgradeContentQueue.close()
                    })
                    context.read()
                }
            } ?: throw IllegalStateException("Response has been already sent")
        }
    }

    override suspend fun responseChannel(): WriteChannel = response.responseChannel()
}