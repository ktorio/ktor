/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation", "PublicApiImplicitType", "unused")
@Deprecated("This is going to be removed", level = DeprecationLevel.HIDDEN)
val ioThreadGroup = ThreadGroup("io-pool-group")

/**
 * The default I/O coroutine dispatcher
 */
@Suppress("DEPRECATION", "unused")
@Deprecated(
    "Use Dispatchers.IO instead for both blocking and non-blocking I/O",
    replaceWith = ReplaceWith("Dispatchers.IO", "kotlinx.coroutines.Dispatchers"),
    level = DeprecationLevel.ERROR
)
val ioCoroutineDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO

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
