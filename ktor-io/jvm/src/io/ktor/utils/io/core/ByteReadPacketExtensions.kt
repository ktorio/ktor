@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import java.nio.*

public actual inline fun ByteReadPacket(
    array: ByteArray,
    offset: Int,
    length: Int,
    crossinline block: (ByteArray) -> Unit
): ByteReadPacket {
    return ByteReadPacket(ByteBuffer.wrap(array, offset, length)) { block(array) }
}

public fun ByteReadPacket(bb: ByteBuffer, release: (ByteBuffer) -> Unit = {}): ByteReadPacket {
    val pool = poolFor(bb, release)
    val view = pool.borrow().apply { resetForRead() }
    return ByteReadPacket(view, pool)
}

private fun poolFor(bb: ByteBuffer, release: (ByteBuffer) -> Unit): ObjectPool<ChunkBuffer> {
    return SingleByteBufferPool(bb, release)
}

private class SingleByteBufferPool(
    val instance: ByteBuffer,
    val release: (ByteBuffer) -> Unit
) : SingleInstancePool<ChunkBuffer>() {
    override fun produceInstance(): ChunkBuffer {
        return ChunkBuffer(instance, this)
    }

    override fun disposeInstance(instance: ChunkBuffer) {
        release(this.instance)
    }
}
