package io.ktor.http.cio.websocket

import io.ktor.util.*
import java.nio.*

class SimpleFrameCollector @Deprecated("Internal Api") constructor() {
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
        remaining -= bb.moveTo(buffer!!, remaining)
    }

    fun take(maskKey: Int?): ByteBuffer = buffer!!.run {
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