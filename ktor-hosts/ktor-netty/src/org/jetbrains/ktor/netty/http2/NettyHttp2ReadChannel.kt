package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2ReadChannel(val streamId: Int, val context: ChannelHandlerContext) : ReadChannel {
    private val sourceBuffers = LinkedList<ByteBuf>()

    private var destinationBuffer: ByteBuffer? = null
    private val currentHandler = AtomicReference<Continuation<Int>?>()

    @Volatile
    private var eof: Boolean = false

    @Volatile
    private var total: Long = 0L

    private val meet = Meeting(2) {
        meet()
    }

    val listener = object : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is Http2Frame) {
                handleRequest(ctx, msg)
            } else {
                ctx.fireChannelRead(msg)
            }
        }

        fun handleRequest(ctx: ChannelHandlerContext, call: Http2Frame) {
            when (call) {
                is Http2DataFrame -> {
                    if (call.streamId() == streamId) {
                        sourceBuffers.add(call.content().retain())
                        eof = call.isEndStream

                        if (sourceBuffers.size == 1) {
                            meet.acknowledge()
                        }
                    }
                }
            }
        }
    }

    override suspend fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining()) return 0

        return suspendCoroutine { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Read operation is already in progress")
            }

            if (eof && sourceBuffers.isEmpty()) {
                currentHandler.set(null)
                continuation.resume(-1)
            } else {
                destinationBuffer = dst
                meet.acknowledge()
            }
        }
    }

    private fun meet() {
        val continuation = currentHandler.getAndSet(null)!!
        val dst = destinationBuffer!!
        destinationBuffer = null

        var total = 0

        while (sourceBuffers.isNotEmpty()) {
            val src = sourceBuffers.first()

            total += src.safeReadTo(dst)

            if (src.isReadable) {
                break
            } else {
                src.release()
                sourceBuffers.removeFirst()
            }
        }

        meet.reset()
        if (sourceBuffers.isNotEmpty()) {
            meet.acknowledge() // increment available
        }

        context.writeAndFlush(DefaultHttp2WindowUpdateFrame(Math.max(9, total))) // TODO not precise enough
        this.total += total

        if (total == 0 && sourceBuffers.isEmpty() && eof) {
            continuation.resume(-1)
        } else {
            continuation.resume(total)
        }
    }

    override fun close() {
        // TODO close
    }

    private fun ByteBuf.safeReadTo(dst: ByteBuffer, count: Int = Math.min(readableBytes(), dst.remaining())): Int {
        if (count < dst.remaining()) {
            val sub = dst.slice()
            sub.limit(count)
            readBytes(sub)
            dst.position(dst.position() + count)
        } else {
            readBytes(dst)
        }

        return count
    }
}
