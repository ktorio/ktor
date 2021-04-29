/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.pool

import io.ktor.utils.io.core.*
import java.nio.*
import java.nio.ByteOrder

private const val DEFAULT_POOL_CAPACITY: Int = 2000

private const val DEFAULT_BUFFER_SIZE: Int = 4096

@ExperimentalIoApi
public class ByteBufferPool(
    capacity: Int = DEFAULT_POOL_CAPACITY,
    public val bufferSize: Int = DEFAULT_BUFFER_SIZE
) : DefaultPool<ByteBuffer>(capacity) {

    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(bufferSize)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply {
        clear()
        order(ByteOrder.BIG_ENDIAN)
    }

    override fun validateInstance(instance: ByteBuffer) {
        check(instance.capacity() == bufferSize)
        check(!instance.isDirect)
    }
}

@ExperimentalIoApi
public class DirectByteBufferPool(
    capacity: Int = DEFAULT_POOL_CAPACITY,
    public val bufferSize: Int = DEFAULT_BUFFER_SIZE
) : DefaultPool<ByteBuffer>(capacity) {

    override fun produceInstance(): ByteBuffer = ByteBuffer.allocateDirect(bufferSize)!!

    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply {
        clear()
        order(ByteOrder.BIG_ENDIAN)
    }

    override fun validateInstance(instance: ByteBuffer) {
        check(instance.capacity() == bufferSize)
        check(instance.isDirect)
    }
}
