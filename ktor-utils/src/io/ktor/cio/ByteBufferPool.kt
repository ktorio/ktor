package io.ktor.cio

import kotlinx.io.pool.*
import java.nio.*


internal const val DEFAULT_BUFFER_SIZE = 4098
internal const val DEFAULT_KTOR_POOL_SIZE = 2048

object KtorDefaultPool : DefaultPool<ByteBuffer>(DEFAULT_KTOR_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}

inline fun <T : Any, R> ObjectPool<T>.use(block: (T) -> R): R {
    val item = borrow()
    try {
        return block(item)
    } finally {
        recycle(item)
    }
}
