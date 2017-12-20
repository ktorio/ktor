package io.ktor.cio

import kotlinx.io.pool.*
import java.nio.*


internal val DEFAULT_BUFFER_SIZE = 4098
internal val DEFAULT_KTOR_POOL_SIZE = 2048

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

object KtorDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_KTOR_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}

suspend fun <T : Any> ObjectPool<T>.use(block: suspend (T) -> Unit) {
    val item = borrow()
    try {
        block(item)
    } finally {
        recycle(item)
    }
}
