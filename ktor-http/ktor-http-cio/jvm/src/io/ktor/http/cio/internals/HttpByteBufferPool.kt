package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation")
@InternalAPI

const val DEFAULT_BYTE_BUFFER_POOL_SIZE: Int = 4096
@Suppress("KDocMissingDocumentation")
@InternalAPI
const val DEFAULT_BYTE_BUFFER_BUFFER_SIZE: Int = 4096

@Suppress("KDocMissingDocumentation")
@InternalAPI
val DefaultByteBufferPool: ObjectPool<ByteBuffer> =
    DirectByteBufferPool(DEFAULT_BYTE_BUFFER_POOL_SIZE, DEFAULT_BYTE_BUFFER_BUFFER_SIZE)

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
