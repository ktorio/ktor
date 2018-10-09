package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
@Deprecated("This is going to be removed")
val ioThreadGroup = ThreadGroup("io-pool-group")

private val cpuCount = Runtime.getRuntime().availableProcessors()

/**
 * The default I/O coroutine dispatcher
 */
@Suppress("DEPRECATION")
@Deprecated(
    "Use Dispatchers.IO instead for both blocking and non-blocking I/O",
    replaceWith = ReplaceWith("Dispatchers.IO", "kotlinx.coroutines.Dispatchers")
)
val ioCoroutineDispatcher: CoroutineDispatcher = IOCoroutineDispatcher(maxOf(2, (cpuCount * 2 / 3)))

/**
 * Byte buffer pool for UDP datagrams
 */
@InternalAPI
val DefaultDatagramByteBufferPool: ObjectPool<ByteBuffer> = DirectByteBufferPool(MAX_DATAGRAM_SIZE, 2048)

internal class DirectByteBufferPool(private val bufferSize: Int, size: Int) : DefaultPool<ByteBuffer>(size) {
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
