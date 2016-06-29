package org.jetbrains.ktor.jetty

import org.eclipse.jetty.io.*
import org.eclipse.jetty.util.*
import org.jetbrains.ktor.nio.*
import java.nio.*

internal class EndPointWriteChannel(val endPoint: EndPoint) : WriteChannel {
    @Volatile
    private var handler: AsyncHandler? = null
    private var positionBefore: Int = 0
    private var bb: ByteBuffer? = null

    private val callback = object : Callback {
        override fun succeeded() {
            val h = handler
            val buffer = bb!!
            val size = buffer.position() - positionBefore

            handler = null
            bb = null

            h?.success(size)
        }

        override fun failed(x: Throwable?) {
            handler?.failed(x ?: Exception())
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        bb = src
        positionBefore = src.position()
        this.handler = handler

        endPoint.write(callback, src)
    }

    override fun requestFlush() {
        // looks like there is nothing to do here
    }

    override fun close() {
        endPoint.close()
    }
}