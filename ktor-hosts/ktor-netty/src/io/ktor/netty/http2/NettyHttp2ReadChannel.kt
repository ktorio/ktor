package io.ktor.netty.http2

import io.netty.buffer.*
import io.netty.handler.codec.http2.*
import io.ktor.cio.*
import io.ktor.pipeline.*
import java.nio.*
import java.nio.channels.*

internal class NettyHttp2ReadChannel(val queue: SuspendQueue<Http2DataFrame>) : ReadChannel {
    private var last: Http2DataFrame? = null
    private var eof = false

    override suspend fun read(dst: ByteBuffer): Int {
        return when {
            eof -> -1
            last != null -> readImpl(last!!, dst)
            else -> readSuspend(dst)
        }
    }

    private suspend fun readSuspend(dst: ByteBuffer): Int {
        val frame = queue.pull() ?: return -1
        return readImpl(frame, dst)
    }

    private fun readImpl(frame: Http2DataFrame, dst: ByteBuffer): Int {
        if (!frame.content().isReadable) {
            last = null
            eof = true
            frame.release()
            return -1
        }

        val copied = frame.content().safeReadTo(dst)

        if (!frame.content().isReadable) {
            last = null
            if (frame.isEndStream) eof = true
            frame.release()
        } else {
            last = frame
        }

        return copied
    }

    override fun close() {
        queue.cancel(ClosedChannelException())
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
