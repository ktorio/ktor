@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

public actual fun ByteReadPacket(array: ByteArray, offset: Int, length: Int, block: (ByteArray) -> Unit): ByteReadPacket {
    if (length == 0) {
        block(array)
        return ByteReadPacket.Empty
    }

    val pool = object : SingleInstancePool<ChunkBuffer>() {
        private var pinned: Pinned<*>? = null

        override fun produceInstance(): ChunkBuffer {
            check(pinned == null) { "This implementation can pin only once." }

            val content = array.pin()
            val base = content.addressOf(offset)
            pinned = content

            @Suppress("DEPRECATION")
            return IoBuffer(Memory.of(base, length), null)
        }

        override fun disposeInstance(instance: ChunkBuffer) {
            check(pinned != null) { "The array hasn't been pinned yet" }
            @Suppress("DEPRECATION")
            check(instance is IoBuffer) { "Only IoBuffer could be recycled" }
            block(array)
            pinned?.unpin()
            pinned = null
        }
    }

    return ByteReadPacket(pool.borrow().apply { resetForRead() }, pool)
}
