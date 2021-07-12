package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.contracts.*
import kotlin.native.concurrent.*

@PublishedApi
@SharedImmutable
internal val MAX_SIZE: size_t = size_t.MAX_VALUE

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Int, origin: ChunkBuffer?): ChunkBuffer {
    return ChunkBuffer(Memory.of(ptr, lengthInBytes), origin, null)
}

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Long, origin: ChunkBuffer?): ChunkBuffer {
    return ChunkBuffer(Memory.of(ptr, lengthInBytes), origin, null)
}

@ThreadLocal
private object BufferPoolNativeWorkaround : DefaultPool<ChunkBuffer>(BUFFER_VIEW_POOL_SIZE) {
    override fun produceInstance(): ChunkBuffer {
        return ChunkBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null, this)
    }

    override fun clearInstance(instance: ChunkBuffer): ChunkBuffer {
        return super.clearInstance(instance).apply {
            unpark()
            reset()
        }
    }

    override fun validateInstance(instance: ChunkBuffer) {
        super.validateInstance(instance)

        require(instance.referenceCount == 0) {
            "unable to recycle buffer: buffer view is in use (refCount = ${instance.referenceCount})"
        }
        require(instance.origin == null) { "Unable to recycle buffer view: view copy shouldn't be recycled" }
    }

    override fun disposeInstance(instance: ChunkBuffer) {
        require(instance.referenceCount == 0) {
            "Couldn't dispose buffer: it is still in-use: refCount = ${instance.referenceCount}"
        }
        nativeHeap.free(instance.memory)
    }
}

public fun Buffer.readFully(pointer: CPointer<ByteVar>, offset: Int, length: Int) {
    readFully(pointer, offset.toLong(), length)
}

public fun Buffer.readFully(pointer: CPointer<ByteVar>, offset: Long, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")
    readExact(length, "content") { memory, start ->
        memory.copyTo(pointer, start.toLong(), length.toLong(), offset)
    }
}

public fun Buffer.readAvailable(pointer: CPointer<ByteVar>, offset: Int, length: Int): Int {
    return readAvailable(pointer, offset.toLong(), length)
}

public fun Buffer.readAvailable(pointer: CPointer<ByteVar>, offset: Long, length: Int): Int {
    val available = readRemaining
    if (available == 0) return -1
    val resultSize = minOf(available, length)
    readFully(pointer, offset, resultSize)
    return resultSize
}

public fun Buffer.writeFully(pointer: CPointer<ByteVar>, offset: Int, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")

    writeExact(length, "content") { memory, start ->
        pointer.copyTo(memory, offset, length, start)
    }
}

public fun Buffer.writeFully(pointer: CPointer<ByteVar>, offset: Long, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")

    writeExact(length, "content") { memory, start ->
        pointer.copyTo(memory, offset, length.toLong(), start.toLong())
    }
}

@OptIn(ExperimentalContracts::class)
public inline fun Buffer.readDirect(block: (CPointer<ByteVar>) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, _ ->
        block(memory.pointer.plus(start)!!)
    }
}

@OptIn(ExperimentalContracts::class)
public inline fun Buffer.writeDirect(block: (CPointer<ByteVar>) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, _ ->
        block(memory.pointer.plus(start)!!)
    }
}
