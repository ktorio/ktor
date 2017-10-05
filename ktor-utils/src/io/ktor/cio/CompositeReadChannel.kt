package io.ktor.cio

import java.nio.*

class CompositeReadChannel(chain: Sequence<() -> ReadChannel>) : ReadChannel {
    private val iterator = chain.iterator()
    private var current: ReadChannel? = null
    private var closed = false

    suspend override fun read(dst: ByteBuffer): Int {
        do {
            val source = ensureCurrent() ?: return -1
            val count = source.read(dst)
            if (count > 0)
                return count
            switchCurrent()
        } while (count == -1)
        return -1
    }

    override fun close() {
        switchCurrent()
        closed = true
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