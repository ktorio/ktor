package io.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import io.ktor.application.*
import io.ktor.host.*
import io.ktor.response.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue,
                                    hostCoroutineContext: CoroutineContext,
                                    userCoroutineContext: CoroutineContext) : BaseApplicationCall(application) {

    override val bufferPool = NettyByteBufferPool(context)
    override val request = NettyApplicationRequest(this, httpRequest, context, contentQueue)
    override val response = NettyApplicationResponse(this, context, hostCoroutineContext, userCoroutineContext)

    private val closed = AtomicBoolean(false)

    init {
        response.pipeline.intercept(ApplicationSendPipeline.Host) {
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

}