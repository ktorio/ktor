package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.*

internal const val DEFAULT_BUFFER_SIZE: Int = 4096

/**
 * Invoke [block] function with a temporary [Buffer] instance of the specified [size] in bytes.
 * The provided instance shouldn't be captured and used outside of the [block] otherwise an undefined behaviour
 * may occur including crash and/or data corruption.
 */
public inline fun <R> withBuffer(size: Int, block: Buffer.() -> R): R {
    return with(Buffer(DefaultAllocator.alloc(size)), block)
}

/**
 * Invoke [block] function with a temporary [Buffer] instance taken from the specified [pool].
 * Depending on the pool it may be safe or unsafe to capture and use the provided buffer outside of the [block].
 * Usually it is always recommended to NOT capture an instance outside.
 */
public inline fun <R> withBuffer(pool: ObjectPool<Buffer>, block: Buffer.() -> R): R {
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
internal val DefaultChunkedBufferPool: ObjectPool<ChunkBuffer> = DefaultBufferPool()

internal class DefaultBufferPool(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    capacity: Int = 1000,
    private val allocator: Allocator = DefaultAllocator
) : DefaultPool<ChunkBuffer>(capacity) {

    override fun produceInstance(): ChunkBuffer {
        return ChunkBuffer(allocator.alloc(bufferSize), null, this)
    }

    override fun disposeInstance(instance: ChunkBuffer) {
        allocator.free(instance.memory)
        super.disposeInstance(instance)
        instance.unlink()
    }

    override fun validateInstance(instance: ChunkBuffer) {
        super.validateInstance(instance)

        check(instance !== ChunkBuffer.Empty) { "ChunkBuffer.Empty couldn't be recycled" }
        check(instance !== Buffer.Empty) { "Empty instance couldn't be recycled" }
        check(instance.referenceCount == 0) { "Unable to clear buffer: it is still in use." }
        check(instance.next == null) { "Recycled instance shouldn't be a part of a chain." }
        check(instance.origin == null) { "Recycled instance shouldn't be a view or another buffer." }
    }

    override fun clearInstance(instance: ChunkBuffer): ChunkBuffer {
        return super.clearInstance(instance).apply {
            unpark()
            reset()
        }
    }
}
