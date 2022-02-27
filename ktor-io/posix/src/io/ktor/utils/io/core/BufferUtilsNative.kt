package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.contracts.*
import kotlin.native.concurrent.*

@OptIn(UnsafeNumber::class)
@PublishedApi
internal val MAX_SIZE: size_t = size_t.MAX_VALUE

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Int, origin: ChunkBuffer?): ChunkBuffer {
    return ChunkBuffer(Memory.of(ptr, lengthInBytes), origin, null)
}

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Long, origin: ChunkBuffer?): ChunkBuffer {
    return ChunkBuffer(Memory.of(ptr, lengthInBytes), origin, null)
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
