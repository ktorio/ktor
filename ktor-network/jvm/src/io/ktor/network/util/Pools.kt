/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.pool.*
import io.ktor.utils.io.pool.DirectByteBufferPool
import java.nio.*

@Suppress("KDocMissingDocumentation", "PublicApiImplicitType", "unused")
@Deprecated("This is going to be removed", level = DeprecationLevel.HIDDEN)
public val ioThreadGroup: ThreadGroup = ThreadGroup("io-pool-group")

/**
 * The default I/O coroutine dispatcher
 */
@Suppress("DEPRECATION", "unused")
@Deprecated(
    "Use Dispatchers.IO instead for both blocking and non-blocking I/O",
    replaceWith = ReplaceWith("Dispatchers.IO", "kotlinx.coroutines.Dispatchers"),
    level = DeprecationLevel.HIDDEN
)
public val ioCoroutineDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO

@Suppress("KDocMissingDocumentation")
@InternalAPI
internal val DEFAULT_BYTE_BUFFER_POOL_SIZE: Int = 4096

@Suppress("KDocMissingDocumentation")
@InternalAPI
internal const val DEFAULT_BYTE_BUFFER_BUFFER_SIZE: Int = 4096

/**
 * Byte buffer pool for general-purpose buffers.
 */
@InternalAPI
public val DefaultByteBufferPool: ObjectPool<ByteBuffer> =
    DirectByteBufferPool(DEFAULT_BYTE_BUFFER_POOL_SIZE, DEFAULT_BYTE_BUFFER_BUFFER_SIZE)

/**
 * Byte buffer pool for UDP datagrams
 */
@InternalAPI
public val DefaultDatagramByteBufferPool: ObjectPool<ByteBuffer> = io.ktor.utils.io.pool.DirectByteBufferPool(2048, MAX_DATAGRAM_SIZE)

@Deprecated(
    level = DeprecationLevel.WARNING,
    message = "ByteBufferPool is moved to `io` module",
    replaceWith = ReplaceWith("ByteBufferPool", "io.ktor.utils.io.pool.ByteBufferPool")
)
internal class DirectByteBufferPool(private val bufferSize: Int, size: Int) : DefaultPool<ByteBuffer>(size) {
    override fun produceInstance(): ByteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        instance.order(ByteOrder.BIG_ENDIAN)
        return instance
    }

    override fun validateInstance(instance: ByteBuffer) {
        require(instance.isDirect)
        require(instance.capacity() == bufferSize)
    }
}
