package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.*
import java.util.concurrent.atomic.*

internal class NettyHttp2ReadChannel(val streamId: Int, val context: ChannelHandlerContext) : ReadChannel {
    private val sourceBuffers = LinkedList<ByteBuf>()

    private var destinationBuffer: ByteBuffer? = null
    private val currentHandler = AtomicReference<AsyncHandler?>()

    @Volatile
    private var eof: Boolean = false

    @Volatile
    private var total: Long = 0L

    private val meet = Meeting(2) {
        meet()
    }

    val listener = object : SimpleChannelInboundHandler<Http2Frame>() {
        override fun channelRead0(ctx: ChannelHandlerContext, msg: Http2Frame) {
            when (msg) {
                is Http2DataFrame -> {
                    if (msg.streamId() == streamId) {
                        sourceBuffers.add(msg.content().retain())
                        eof = msg.isEndStream

                        if (sourceBuffers.size == 1) {
                            meet.acknowledge()
                        }
                    }
                }
            }
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Read operation is already in progress")
        }

        if (eof && sourceBuffers.isEmpty()) {
            currentHandler.set(null)
            handler.successEnd()
        } else {
            destinationBuffer = dst
            meet.acknowledge()
        }
    }

    private fun meet() {
        val handler = currentHandler.getAndSet(null)!!
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
            handler.successEnd()
        } else {
            handler.success(total)
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
