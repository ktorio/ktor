package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import java.io.*

internal class NettyAsyncStream(val request: HttpRequest, val context: ChannelHandlerContext) : OutputStream() {
    private val buffer = context.alloc().buffer(8192)
    private var lastContentWritten = false

    override fun write(b: Int) {
        require(lastContentWritten == false) { "You can't write after the last chunk was written" }

        buffer.writeByte(b)
        if (buffer.writableBytes() == 0) {
            flush()
        }
    }

    tailrec
    override fun write(b: ByteArray, off: Int, len: Int) {
        require(lastContentWritten == false) { "You can't write after the last chunk was written" }

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
        if (!lastContentWritten && buffer.readableBytes() > 0) {
            context.writeAndFlush(DefaultHttpContent(buffer.copy()))
            buffer.writerIndex(0)
        }
    }

    fun writeFile(file: File, position: Long, length: Long) {
        flush()
        context.write(DefaultFileRegion(file, position, length))
    }

    fun writeStream(stream: InputStream) {
        flush()
        context.write(HttpChunkedInput(ChunkedStream(stream.buffered())))
        lastContentWritten = true
    }

    override fun close() {
        flush()
        if (!lastContentWritten) {
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
            lastContentWritten = true
        } else if (noKeepAlive()) {
            context.close()
        }
    }

    private fun ChannelFuture.scheduleClose() {
        if (noKeepAlive()) {
            addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun noKeepAlive() = !HttpHeaders.isKeepAlive(request)
}