package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
val DefaultByteBufferPool: ObjectPool<ByteBuffer> = DirectByteBufferPool(4096, 2048)

private class DirectByteBufferPool(val bufferSize: Int, size: Int) : DefaultPool<ByteBuffer>(size) {
    override fun produceInstance(): ByteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        return instance
    }

    override fun validateInstance(instance: ByteBuffer) {
        require(instance.isDirect)
        require(instance.capacity() == bufferSize)
    }
}
