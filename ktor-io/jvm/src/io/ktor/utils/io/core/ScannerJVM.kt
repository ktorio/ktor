package io.ktor.utils.io.core

import java.nio.ByteBuffer

internal actual fun Buffer.discardUntilDelimiterImpl(delimiter: Byte): Int {
    return if (hasArray()) discardUntilDelimiterImplArrays(this, delimiter)
    else discardUntilDelimiterImplMemory(this, delimiter)
}

private fun discardUntilDelimiterImplArrays(buffer: Buffer, delimiter: Byte): Int {
    val bb = buffer.memory.buffer
    val array = bb.array()!!
    val start = bb.arrayOffset() + bb.position() + buffer.readPosition
    var i = start
    val end = i + buffer.readRemaining
    if (end <= array.size) {
        while (i < end) {
            if (array[i] == delimiter) break
            i++
        }
    }

    buffer.discardUntilIndex(i)
    return i - start
}

internal actual fun Buffer.discardUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte): Int {
    return if (hasArray()) discardUntilDelimitersImplArrays(this, delimiter1, delimiter2)
        else discardUntilDelimitersImplMemory(this, delimiter1, delimiter2)
}

private fun discardUntilDelimitersImplArrays(buffer: Buffer, delimiter1: Byte, delimiter2: Byte): Int {
    val bb = buffer.memory.buffer
    val array = bb.array()!!
    val start = bb.arrayOffset() + bb.position() + buffer.readPosition
    var i = start
    val end = i + buffer.readRemaining
    if (end <= array.size) {
        while (i < end) {
            val v = array[i]
            if (v == delimiter1 || v == delimiter2) break
            i++
        }
    }

    buffer.discardUntilIndex(i)
    return i - start
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimiterImpl(delimiter: Byte,
                                                    dst: ByteArray, offset: Int, length: Int): Int {
    assert(offset >= 0)
    assert(length >= 0)
    assert(offset + length <= dst.size)

    return if (hasArray()) readUntilDelimiterArrays(this, delimiter, dst, offset, length)
    else readUntilDelimiterDirect(delimiter, dst, offset, length)
}

private fun Buffer.readUntilDelimiterDirect(
    delimiter: Byte,
    dst: ByteArray, offset: Int, length: Int
): Int {
    val copied = copyUntil({ it == delimiter }, dst, offset, length)
    discardExact(copied)
    return copied
}

private fun readUntilDelimiterArrays(buffer: Buffer, delimiter: Byte, dst: ByteArray, offset: Int, length: Int): Int {
    val copied = buffer.memory.buffer.copyUntilArrays(
        { it == delimiter },
        buffer.readPosition,
        dst,
        offset,
        minOf(length, buffer.readRemaining)
    )
    buffer.discardExact(copied)
    return copied
}

internal actual fun Buffer.readUntilDelimitersImpl(
    delimiter1: Byte, delimiter2: Byte,
    dst: ByteArray, offset: Int, length: Int): Int {
    assert(offset >= 0)
    assert(length >= 0)
    assert(offset + length <= dst.size)
    assert(delimiter1 != delimiter2)

    return if (hasArray()) readUntilDelimitersArrays(delimiter1, delimiter2, dst, offset, length)
    else readUntilDelimitersDirect(delimiter1, delimiter2, dst, offset, length)
}

private fun Buffer.readUntilDelimitersDirect(delimiter1: Byte, delimiter2: Byte,
                                                 dst: ByteArray, offset: Int, length: Int): Int {
    val copied = copyUntil({ it == delimiter1 || it == delimiter2 }, dst, offset, length)
    discardExact(copied)
    return copied
}

private fun Buffer.readUntilDelimitersArrays(delimiter1: Byte, delimiter2: Byte,
                                                 dst: ByteArray, offset: Int, length: Int): Int {
    val copied = memory.buffer.copyUntilArrays({ it == delimiter1 || it == delimiter2 },
        readPosition, dst, offset, minOf(length, readRemaining))
    discardExact(copied)
    return copied
}

internal actual fun Buffer.readUntilDelimiterImpl(delimiter: Byte, dst: Output): Int {
    return if (hasArray()) readUntilDelimiterArrays(delimiter, dst)
    else readUntilDelimiterDirect(delimiter, dst)
}

internal fun Buffer.readUntilDelimiterDirect(delimiter: Byte, dst: Output): Int {
    return copyUntil({ it == delimiter }, dst)
}

internal fun Buffer.readUntilDelimiterArrays(delimiter: Byte, dst: Output): Int {
    return copyUntilArrays({ it == delimiter }, dst)
}

internal actual fun Buffer.readUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte, dst: Output): Int {
    assert(delimiter1 != delimiter2)

    return if (hasArray()) readUntilDelimitersArrays(delimiter1, delimiter2, dst)
    else readUntilDelimitersDirect(delimiter1, delimiter2, dst)
}

internal fun Buffer.readUntilDelimitersDirect(delimiter1: Byte, delimiter2: Byte,
                                                  dst: Output): Int {
    return copyUntil({ it == delimiter1 || it == delimiter2 }, dst)
}

internal fun Buffer.readUntilDelimitersArrays(delimiter1: Byte, delimiter2: Byte,
                                                  dst: Output): Int {
    return copyUntilArrays({ it == delimiter1 || it == delimiter2 }, dst)
}

@Deprecated("Rewrite to Memory.copyTo")
private inline fun ByteBuffer.copyUntilDirect(predicate: (Byte) -> Boolean,
                                              dst: ByteArray, offset: Int, length: Int): Int {
    val start = position()
    var i = start
    val end = i + length
    while (i < limit() && i < end) {
        if (predicate(this[i])) break
        i++
    }

    val copied = i - start
    get(dst, offset, copied)
    return copied
}

private inline fun ByteBuffer.copyUntilArrays(predicate: (Byte) -> Boolean,
                                              bufferOffset: Int,
                                              dst: ByteArray, offset: Int, length: Int): Int {

    val array = array()!!
    val start = bufferOffset + position() + arrayOffset()
    var i = start
    val end = i + minOf(length, remaining())
    if (end <= array.size) {
        while (i < end) {
            if (predicate(array[i])) break
            i++
        }
    }

    val copied = i - start
    System.arraycopy(array, start, dst, offset, copied)
    return copied
}

private inline fun Buffer.copyUntilArrays(predicate: (Byte) -> Boolean,
                                              dst: Output): Int {
    val bb = memory.buffer
    val array = bb.array()!!
    var i = bb.position() + bb.arrayOffset() + readPosition
    val sourceEndPosition = bb.position() + bb.arrayOffset() + writePosition
    var copiedTotal = 0

    dst.writeWhile { chunk ->
        val start = i
        val end = minOf(i + chunk.writeRemaining, sourceEndPosition)

        if (end <= array.size) {
            while (i < end) {
                if (predicate(array[i])) break
                i++
            }
        }

        val size = i - start

        chunk.writeFully(array, start, size)

        copiedTotal += size
        !chunk.canWrite() && i < sourceEndPosition
    }

    discardUntilIndex(i)
    return copiedTotal
}
