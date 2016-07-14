package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.*
import org.jetbrains.ktor.netty.*
import java.net.*
import java.util.*
import java.util.concurrent.atomic.*

internal abstract class Http2StreamChannel(parent: Channel, val streamId: Int, val context: ChannelHandlerContext) : AbstractChannel(parent) {
    private val closed = AtomicBoolean()
    private val readPending = AtomicBoolean()
    private val config = DefaultChannelConfig(this)
    private val inboundBuffer = ArrayDeque<Any>(4)

    override fun metadata() = Internals.METADATA

    override fun config() = config

    override fun isOpen() = !closed.get() && parent().isOpen

    override fun isActive() = !closed.get() && parent().isActive

    override fun parent(): Channel = super.parent()!!

    override fun newUnsafe(): AbstractChannel.AbstractUnsafe {
        return Unsafe()
    }

    override fun isCompatible(loop: EventLoop) = true

    override fun localAddress0(): SocketAddress? = parent().localAddress()
    override fun remoteAddress0(): SocketAddress? = parent().remoteAddress()

    override fun doBind(localAddress: SocketAddress) {
        throw UnsupportedOperationException()
    }

    override fun doDisconnect() {
        throw UnsupportedOperationException()
    }

    public override fun doClose() {
        closed.set(true)
        while (!inboundBuffer.isEmpty()) {
            ReferenceCountUtil.release(inboundBuffer.poll())
        }
    }

    override fun doBeginRead() {
        if (readPending.get()) {
            return
        }

        val allocHandle = unsafe().recvBufAllocHandle()
        allocHandle.reset(config())
        if (inboundBuffer.isEmpty()) {
            readPending.set(true)
            return
        }

        do {
            val m = inboundBuffer.poll() ?: break
            if (!handleRead(m, allocHandle)) {
                // Channel closed, and already cleaned up.
                return
            }
        } while (allocHandle.continueReading())

        allocHandle.readComplete()
        pipeline().fireChannelReadComplete()
    }

    override fun doWrite(outbound: ChannelOutboundBuffer) {
        if (closed.get()) {
            throw StreamBufferingEncoder.Http2ChannelClosedException()
        }

        val retained = ArrayList<Any>()
        while (!outbound.isEmpty) {
            val message = outbound.current()
            if (message != null) {
                retained += ReferenceCountUtil.retain(message)
                outbound.remove()
            }
        }

        context.executeInLoop {
            for (message in retained) {
                try {
                    beforeWrite(message)
                } catch (t: Throwable) {
                    context.fireExceptionCaught(t)
                }
            }
        }

        while (!outbound.isEmpty) {
            val message = outbound.current()
            if (message != null) {

            }
            outbound.remove()
        }

        doWriteComplete()
    }

    private fun doWriteComplete() {
        context.flush()
    }

    private fun beforeWrite(msg: Any) {
        if (msg !is Http2StreamFrame) {
            ReferenceCountUtil.release(msg)
            throw IllegalArgumentException("Message must be an Http2StreamFrame: " + msg)
        }

        val frame = msg
        if (frame.streamId() != -1) {
            ReferenceCountUtil.release(frame)
            throw IllegalArgumentException("Stream must not be set on the frame")
        }
        frame.setStreamId(streamId)

        writeFromStreamChannel(msg, true)
    }

    abstract fun writeFromStreamChannel(msg: Http2StreamFrame, flush: Boolean)

    fun handleRead(message: Any, allocHandle: RecvByteBufAllocator.Handle = unsafe().recvBufAllocHandle()): Boolean {
        if (closed.get()) {
            allocHandle.readComplete()
            pipeline().fireChannelReadComplete()
            unsafe().close(voidPromise())
            return false
        }

        val numBytesToBeConsumed: Int
        if (message is Http2DataFrame) {
            numBytesToBeConsumed = message.content().readableBytes() + message.padding()
            allocHandle.lastBytesRead(numBytesToBeConsumed)
        } else {
            allocHandle.lastBytesRead(9)
        }

        allocHandle.incMessagesRead(1)
        pipeline().fireChannelRead(message)

        // TODO we have to send window changed events somewhere else!

        return true
    }

    fun fireChildReadComplete() {
        context.executeInLoop(fireChildReadCompleteTask)
    }

    private val fireChildReadCompleteTask = Runnable {
        if (readPending.compareAndSet(true, false)) {
            unsafe().recvBufAllocHandle().readComplete()
            pipeline().fireChannelReadComplete()
        }
    }

    private object Internals {
        var METADATA = ChannelMetadata(false, 16)
    }

    private inner class Unsafe : AbstractChannel.AbstractUnsafe() {
        override fun connect(remoteAddress: SocketAddress,
                             localAddress: SocketAddress, promise: ChannelPromise) {
            promise.setFailure(UnsupportedOperationException())
        }
    }
}