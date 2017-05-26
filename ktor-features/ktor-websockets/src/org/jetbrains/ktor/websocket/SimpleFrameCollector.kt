package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.util.*
import java.nio.*

internal class SimpleFrameCollector {
    private var remaining: Int = 0
    private var buffer: ByteBuffer? = null
    private val maskBuffer = ByteBuffer.allocate(4)

    val hasRemaining: Boolean
        get() = remaining > 0

    fun start(length: Int, bb: ByteBuffer) {
        require(remaining == 0) { throw IllegalStateException("remaining should be 0") }

        remaining = length
        if (buffer == null || buffer!!.capacity() < length) {
            buffer = ByteBuffer.allocate(length)
        }
        buffer!!.clear()

        handle(bb)
    }

    fun handle(bb: ByteBuffer) {
        remaining -= bb.putTo(buffer!!, remaining)
    }

    fun take(maskKey: Int?) = buffer!!.run {
        flip()

        val view = slice()

        if (maskKey != null) {
            maskBuffer.clear()
            maskBuffer.asIntBuffer().put(maskKey)
            maskBuffer.clear()

            view.xor(maskBuffer)
        }

        buffer = null
        view.asReadOnlyBuffer()
    }
}