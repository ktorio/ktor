package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.*
import io.netty.util.collection.*
import org.jetbrains.ktor.netty.*
import java.io.*
import kotlin.properties.*

internal class Multiplexer(val parent: Channel, val handler: ChannelHandler) : ChannelDuplexHandler() {
    private val channels = IntObjectHashMap<Http2StreamChannel>()
    private var context: ChannelHandlerContext by Delegates.notNull()

    init {
        require((handler is ChannelHandlerAdapter && handler.isSharable)
                || handler.javaClass.isAnnotationPresent(ChannelHandler.Sharable::class.java)) { "handler must be Sharable" }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        context = ctx
        super.handlerAdded(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is Http2StreamFrame -> {
                channels[msg.streamId()]?.fireRead(msg) ?: run {
                    release(msg)
                    // TODO send back error
                    throw IOException("Stream ${msg.streamId()} has no associated stream object")
                }
            }
        // TODO go away frame
            else -> {
                super.channelRead(ctx, msg)
            }
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        when (evt) {
            is Http2StreamActiveEvent -> streamActive(evt.streamId())
            is Http2StreamClosedEvent -> streamClosed(evt.streamId())
            else -> super.userEventTriggered(ctx, evt)
        }
    }

    private fun streamActive(streamId: Int) {
        channels[streamId] = streamChannel(streamId)
    }

    private fun streamClosed(streamId: Int) {
        channels[streamId]?.let { channel ->
            context.executeInLoop {
                channel.doClose()
                channel.handleRead(Unit)
            }
        }
    }

    private fun streamChannel(streamId: Int): Http2StreamChannel =
            Http2StreamChannelImpl(streamId).apply {
                pipeline().addLast(handler)

                context.channel().eventLoop().register(this).let { future ->
                    if (future.cause() != null) {
                        if (isRegistered) {
                            close()
                        } else {
                            unsafe().closeForcibly()
                        }
                    }
                }
            }

    private fun Http2StreamChannel.fireRead(msg: Http2Frame) {
        if (isOpen) {
            handleRead(msg) // TODO should we check it before for unsafe.continue ... ? should we put it on the queue instead?
            fireChildReadComplete() // TODO when should we call it?
        }

//        release(msg) // TODO most likely we shouldn't release it here
    }

    private fun release(o: Any) {
        ReferenceCountUtil.release(o)
    }

    private inner class Http2StreamChannelImpl(streamId: Int) : Http2StreamChannel(parent, streamId, context) {
        override fun writeFromStreamChannel(msg: Http2StreamFrame, flush: Boolean) {
            context.executeInLoop {
                write(context, msg, context.newPromise())
                if (flush) {
                    flush(context)
                }
            }
        }
    }
}