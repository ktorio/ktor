package org.jetbrains.ktor.nio

import java.nio.*

class ChannelJoinReadChannel(chain: Sequence<() -> ReadChannel>) : ReadChannel {
    private val iterator = chain.iterator()
    private var current: ReadChannel? = null
    private var closed = false

    override fun close() {
        switchCurrent()
        closed = true
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        val source = ensureCurrent()
        if (source == null) {
            handler.successEnd()
            return
        }

        parentHandler = handler
        buffer = dst

        source.read(dst, childHandler)
    }

    override fun releaseFlush() = ensureCurrent()?.releaseFlush() ?: 0

    @Volatile
    private var buffer: ByteBuffer? = null
    @Volatile
    private var parentHandler: AsyncHandler? = null

    private val childHandler = object : AsyncHandler {
        override fun success(count: Int) {
            withHandler { buffer, handler ->
                handler.success(count)
            }
        }

        override fun successEnd() {
            withHandler { buffer, handler ->
                switchCurrent()
                read(buffer, handler)
            }
        }

        override fun failed(cause: Throwable) {
            withHandler { buffer, handler ->
                handler.failed(cause)
            }
        }
    }

    private inline fun withHandler(block: (ByteBuffer, AsyncHandler) -> Unit) {
        val handler = parentHandler
        val buffer = this.buffer
        parentHandler = null
        this.buffer = null

        if (handler != null && buffer != null) {
            block(buffer, handler)
        }
    }

    private fun ensureCurrent(): ReadChannel? {
        return when {
            closed -> null
            current != null -> current
            iterator.hasNext() -> {
                current = iterator.next().invoke()
                current
            }
            else -> null
        }
    }

    private fun switchCurrent() {
        current?.close()
        current = null
    }
}