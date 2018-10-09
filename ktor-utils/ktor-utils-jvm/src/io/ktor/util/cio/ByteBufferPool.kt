package io.ktor.util.cio

import io.ktor.util.*
import kotlinx.io.pool.*
import java.nio.*


internal const val DEFAULT_BUFFER_SIZE = 4098
internal const val DEFAULT_KTOR_POOL_SIZE = 2048

val KtorDefaultPool = ByteBufferPool()

@InternalAPI
class ByteBufferPool : DefaultPool<ByteBuffer>(DEFAULT_KTOR_POOL_SIZE) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}
