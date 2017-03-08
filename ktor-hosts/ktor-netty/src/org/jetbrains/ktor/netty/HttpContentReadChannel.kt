package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import org.jetbrains.ktor.cio.*
import java.nio.*

internal class HttpContentReadChannel(val queue: NettyContentQueue, val buffered: Boolean = true) : ReadChannel {
    private var lastContent: HttpContent? = null

    override suspend fun read(dst: ByteBuffer): Int {
        check(dst.hasRemaining())
        var total = 0
        while (dst.hasRemaining()) {
            val content = lastContent ?: queue.pull()

            if (content == null) {
                if (total == 0)
                    return -1
                else
                    return total
            }

            val count = content.copyTo(dst)
            total += count
            lastContent = if (content.content().isReadable) content else {
                content.release()
                null
            }

            if (total > 0 && !buffered) {
                break
            }
        }

        return total
    }

    private fun HttpContent.copyTo(buffer: ByteBuffer): Int {
        val size = Math.min(buffer.remaining(), content().readableBytes())
        val oldLimit = buffer.limit()
        buffer.limit(buffer.position() + size)

        content().readBytes(buffer)

        buffer.limit(oldLimit)

        return size
    }

    override fun close() {
        lastContent?.release()
    }
}