package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import java.io.*
import java.nio.*
import java.nio.channels.*

internal class NettyAsyncStream(val request: HttpRequest, val appResponse: NettyApplicationResponse, val context: ChannelHandlerContext) : OutputStream() {
    private val buffer = context.alloc().buffer(8192)
    private var asyncStarted = false

    override fun write(b: Int) {
        buffer.writeByte(b)
        if (buffer.writableBytes() == 0) {
            flush()
        }
    }

    tailrec
    override fun write(b: ByteArray, off: Int, len: Int) {
        val toWrite = Math.min(len, buffer.writableBytes())
        if (toWrite > 0) {
            buffer.writeBytes(b, off, toWrite)
            if (buffer.writableBytes() == 0) {
                flush()
            }
            if (toWrite < len) {
                write(b, off + toWrite, len - toWrite)
            }
        }
    }

    override fun flush() {
        if (buffer.readableBytes() > 0) {
            context.writeAndFlush(DefaultHttpContent(buffer.copy()))
            buffer.writerIndex(0)
        }
    }

    private val bb = ByteBuffer.allocate(8192)
    private val handler = object : CompletionHandler<Int, AsynchronousByteChannel> {
        override fun failed(p0: Throwable?, p1: AsynchronousByteChannel) {
            flush()
            // TODO error
        }

        override fun completed(result: Int, attachment: AsynchronousByteChannel) {
            if (result == -1) {
                finish()
            } else {
                bb.flip()

                buffer.writeBytes(bb)
                context.write(DefaultHttpContent(buffer.copy()))
                buffer.writerIndex(0)

                startRead(attachment)
            }
        }
    }

    private fun startRead(channel: AsynchronousByteChannel) {
        bb.clear()
        channel.read(bb, channel, handler)
    }

    override fun close() {
        flush()
        if (!asyncStarted) {
            finish()
        }
    }

    private fun finish() {
        appResponse.finalize()
    }
}