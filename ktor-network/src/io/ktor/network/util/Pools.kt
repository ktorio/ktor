package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.io.pool.*
import java.nio.*

val ioThreadGroup = ThreadGroup("io-pool-group")

private val cpuCount = Runtime.getRuntime().availableProcessors()

val ioCoroutineDispatcher: CoroutineDispatcher = IOCoroutineDispatcher(maxOf(2, (cpuCount * 2 / 3)))

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
