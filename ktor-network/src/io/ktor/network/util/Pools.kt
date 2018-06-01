package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.scheduling.*
import kotlinx.io.pool.*
import java.nio.*

val ioThreadGroup = ThreadGroup("io-pool-group")

val ioCoroutineDispatcher: CoroutineDispatcher = ExperimentalCoroutineDispatcher()

val DefaultDatagramByteBufferPool: ObjectPool<ByteBuffer> = DirectByteBufferPool(MAX_DATAGRAM_SIZE, 2048)

internal class DirectByteBufferPool(val bufferSize: Int, size: Int) : DefaultPool<ByteBuffer>(size) {
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
