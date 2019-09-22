/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.util.*
import io.ktor.utils.io.pool.*
import java.nio.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
internal val DEFAULT_BYTE_BUFFER_POOL_SIZE: Int = 4096

@Suppress("KDocMissingDocumentation")
@InternalAPI
internal const val DEFAULT_BYTE_BUFFER_BUFFER_SIZE: Int = 4096

@Suppress("KDocMissingDocumentation")
@InternalAPI
val DefaultByteBufferPool: ObjectPool<ByteBuffer> =
    DirectByteBufferPool(DEFAULT_BYTE_BUFFER_BUFFER_SIZE, DEFAULT_BYTE_BUFFER_POOL_SIZE)

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
