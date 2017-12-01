package io.ktor.cio

import kotlinx.io.pool.*
import java.nio.*


internal val DEFAULT_BUFFER_SIZE = 4098

interface ByteBufferPool {
    fun allocate(size: Int): PoolTicket
    fun release(buffer: PoolTicket)
}

interface PoolTicket {
    val buffer: ByteBuffer
}

abstract class ReleasablePoolTicket(private var _buffer: ByteBuffer) : PoolTicket {
    final override val buffer: ByteBuffer
        get() = _buffer.also { if (it === RELEASED) throw IllegalStateException("Buffer already released") }

    fun release() {
        _buffer = RELEASED
    }

    companion object {
        private val RELEASED = ByteBuffer.allocate(0)
    }
}

object NoPool : ByteBufferPool {
    override fun allocate(size: Int): PoolTicket {
        return Ticket(ByteBuffer.allocate(size))
    }

    override fun release(buffer: PoolTicket) {
    }

    private class Ticket(override val buffer: ByteBuffer) : PoolTicket
}

object EmptyByteBufferPool : ObjectPool<ByteBuffer> {
    override val capacity: Int = 0

    override fun borrow(): ByteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)

    override fun dispose() {}

    override fun recycle(instance: ByteBuffer) {}
}

suspend fun <T : Any> ObjectPool<T>.use(block: suspend (T) -> Unit) {
    val item = borrow()
    block(item)
    recycle(item)
}
