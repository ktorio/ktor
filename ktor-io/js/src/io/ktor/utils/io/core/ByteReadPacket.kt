@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import org.khronos.webgl.*

public actual fun ByteReadPacket(
    array: ByteArray,
    offset: Int,
    length: Int,
    block: (ByteArray) -> Unit
): ByteReadPacket {
    val content = array.asDynamic() as Int8Array
    val sub = when {
        offset == 0 && length == array.size -> content.buffer
        else -> content.buffer.slice(offset, offset + length)
    }

    val pool = object : SingleInstancePool<ChunkBuffer>() {
        override fun produceInstance(): ChunkBuffer {
            @Suppress("DEPRECATION")
            return ChunkBuffer(Memory.of(sub), null, this as ObjectPool<ChunkBuffer>)
        }

        override fun disposeInstance(instance: ChunkBuffer) {
            @Suppress("DEPRECATION")
            check(instance is ChunkBuffer) { "Only ChunkBuffer could be recycled" }
            block(array)
        }
    }

    return ByteReadPacket(pool.borrow().apply { resetForRead() }, pool)
}
