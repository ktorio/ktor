package io.ktor.utils.io.internal

import io.ktor.utils.io.pool.*
import java.nio.*

internal val BUFFER_SIZE = getIOIntProperty("BufferSize", 4096)
private val BUFFER_POOL_SIZE = getIOIntProperty("BufferPoolSize", 2048)
private val BUFFER_OBJECT_POOL_SIZE = getIOIntProperty("BufferObjectPoolSize", 1024)

// ------------- standard shared pool objects -------------

internal val BufferPool: ObjectPool<ByteBuffer> =
    object : DefaultPool<ByteBuffer>(BUFFER_POOL_SIZE) {
        override fun produceInstance(): ByteBuffer =
            ByteBuffer.allocateDirect(BUFFER_SIZE)
        override fun clearInstance(instance: ByteBuffer): ByteBuffer =
            instance.also { it.clear() }
        override fun validateInstance(instance: ByteBuffer) {
            require(instance.capacity() == BUFFER_SIZE)
        }
    }

internal val BufferObjectPool: ObjectPool<ReadWriteBufferState.Initial> =
    object: DefaultPool<ReadWriteBufferState.Initial>(BUFFER_OBJECT_POOL_SIZE) {
        override fun produceInstance() =
            ReadWriteBufferState.Initial(BufferPool.borrow())
        override fun disposeInstance(instance: ReadWriteBufferState.Initial) {
            BufferPool.recycle(instance.backingBuffer)
        }
    }

internal val BufferObjectNoPool: ObjectPool<ReadWriteBufferState.Initial> =
    object : NoPoolImpl<ReadWriteBufferState.Initial>() {
        override fun borrow(): ReadWriteBufferState.Initial =
            ReadWriteBufferState.Initial(ByteBuffer.allocateDirect(BUFFER_SIZE))
    }
