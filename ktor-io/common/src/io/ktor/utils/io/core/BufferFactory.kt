package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.DefaultPool
import io.ktor.utils.io.pool.ObjectPool
import kotlin.native.concurrent.ThreadLocal

internal const val DEFAULT_BUFFER_SIZE: Int = 4096

/**
 * Invoke [block] function with a temporary [Buffer] instance of the specified [size] in bytes.
 * The provided instance shouldn't be captured and used outside of the [block] otherwise an undefined behaviour
 * may occur including crash and/or data corruption.
 */
@ExperimentalIoApi
inline fun <R> withBuffer(size: Int, block: Buffer.() -> R): R {
    return with(Buffer(DefaultAllocator.alloc(size)), block)
}

/**
 * Invoke [block] function with a temporary [Buffer] instance taken from the specified [pool].
 * Depending on the pool it may be safe or unsafe to capture and use the provided buffer outside of the [block].
 * Usually it is always recommended to NOT capture an instance outside.
 */
@ExperimentalIoApi
inline fun <R> withBuffer(pool: ObjectPool<Buffer>, block: Buffer.() -> R): R {
    val instance = pool.borrow()
    return try {
        block(instance)
    } finally {
        pool.recycle(instance)
    }
}

/**
 * Invoke [block] function with a temporary [Buffer] instance taken from the specified [pool].
 * Depending on the pool it may be safe or unsafe to capture and use the provided buffer outside of the [block].
 * Usually it is always recommended to NOT capture an instance outside.
 * However since [ChunkBuffer] is reference counted, you can create a [Buffer.duplicate] (this is simply a view) and use
 * it outside of the [block] function but it is important to release the duplicate properly once not needed anymore
 * otherwise memory leak may occur on some platforms.
 */
internal inline fun <R> withChunkBuffer(pool: ObjectPool<ChunkBuffer>, block: ChunkBuffer.() -> R): R {
    val instance = pool.borrow()
    return try {
        block(instance)
    } finally {
        instance.release(pool)
    }
}

@ThreadLocal
@Suppress("DEPRECATION")
internal val DefaultChunkedBufferPool: ObjectPool<IoBuffer> = DefaultBufferPool()

@Suppress("DEPRECATION")
internal class DefaultBufferPool(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,

    capacity: Int = 1000,
    private val allocator: Allocator = DefaultAllocator
) : DefaultPool<IoBuffer>(capacity) {
    override fun produceInstance(): IoBuffer {
        return IoBuffer(allocator.alloc(bufferSize), null)
    }

    override fun disposeInstance(instance: IoBuffer) {
        allocator.free(instance.memory)
        super.disposeInstance(instance)
        instance.unlink()
    }

    override fun validateInstance(instance: IoBuffer) {
        super.validateInstance(instance)

        if (instance === IoBuffer.Empty) {
            error("IoBuffer.Empty couldn't be recycled")
        }

        check(instance !== IoBuffer.Empty) { "Empty instance couldn't be recycled" }
        check(instance !== Buffer.Empty) { "Empty instance couldn't be recycled" }
        check(instance !== ChunkBuffer.Empty) { "Empty instance couldn't be recycled" }

        check(instance.referenceCount == 0) { "Unable to clear buffer: it is still in use." }
        check(instance.next == null) { "Recycled instance shouldn't be a part of a chain." }
        check(instance.origin == null) { "Recycled instance shouldn't be a view or another buffer." }
    }

    override fun clearInstance(instance: IoBuffer): IoBuffer {
        return super.clearInstance(instance).apply {
            unpark()
            reset()
        }
    }
}
