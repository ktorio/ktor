package io.ktor.utils.io.core

import io.ktor.utils.io.bits.get
import io.ktor.utils.io.bits.loadByteArray

/**
 * Discards bytes until [delimiter] occurred
 * @return number of bytes discarded
 */
fun Input.discardUntilDelimiter(delimiter: Byte): Long {
    var discardedTotal = 0L

    takeWhile { chunk ->
        val discarded = chunk.discardUntilDelimiterImpl(delimiter)
        discardedTotal += discarded
        discarded > 0 && !chunk.canRead()
    }

    return discardedTotal
}

/**
 * Discards bytes until of of the specified delimiters [delimiter1] or [delimiter2] occurred
 * @return number of bytes discarded
 */
fun Input.discardUntilDelimiters(delimiter1: Byte, delimiter2: Byte): Long {
    var discardedTotal = 0L

    takeWhile { chunk ->
        val discarded = chunk.discardUntilDelimitersImpl(delimiter1, delimiter2)
        discardedTotal += discarded
        discarded > 0 && !chunk.canRead()
    }

    return discardedTotal
}

/**
 * Copies to [dst] array at [offset] at most [length] bytes or until the specified [delimiter] occurred.
 * @return number of bytes copied
 */
fun Input.readUntilDelimiter(delimiter: Byte, dst: ByteArray, offset: Int = 0, length: Int = dst.size): Int {
    var currentOffset = offset
    var dstRemaining = length

    takeWhile { chunk ->
        val copied = chunk.readUntilDelimiterImpl(delimiter, dst, currentOffset, dstRemaining)
        currentOffset += copied
        dstRemaining -= copied
        dstRemaining > 0 && !chunk.canRead()
    }

    return currentOffset - offset
}

/**
 * Copies to [dst] array at [offset] at most [length] bytes or until one of the specified delimiters
 * [delimiter1] or [delimiter2] occurred.
 * @return number of bytes copied
 */
fun Input.readUntilDelimiters(delimiter1: Byte, delimiter2: Byte,
                              dst: ByteArray, offset: Int = 0, length: Int = dst.size): Int {
    if (delimiter1 == delimiter2) return readUntilDelimiter(delimiter1, dst, offset, length)

    var currentOffset = offset
    var dstRemaining = length

    takeWhile {  chunk ->
        val copied = chunk.readUntilDelimitersImpl(delimiter1, delimiter2, dst, currentOffset, dstRemaining)
        currentOffset += copied
        dstRemaining -= copied
        !chunk.canRead() && dstRemaining > 0
    }

    return currentOffset - offset
}

/**
 * Copies to [dst] output until the specified [delimiter] occurred.
 * @return number of bytes copied
 */
fun Input.readUntilDelimiter(delimiter: Byte, dst: Output): Long {
    var copiedTotal = 0L
    takeWhile { chunk ->
        val copied = chunk.readUntilDelimiterImpl(delimiter, dst)
        copiedTotal += copied
        !chunk.canRead()
    }

    return copiedTotal
}

/**
 * Copies to [dst] output until one of the specified delimiters
 * [delimiter1] or [delimiter2] occurred.
 * @return number of bytes copied
 */
fun Input.readUntilDelimiters(delimiter1: Byte, delimiter2: Byte, dst: Output): Long {
    var copiedTotal = 0L

    takeWhile { chunk ->
        val copied = chunk.readUntilDelimitersImpl(delimiter1, delimiter2, dst)
        copiedTotal += copied
        !chunk.canRead()
    }

    return copiedTotal
}

internal expect fun Buffer.discardUntilDelimiterImpl(delimiter: Byte): Int

internal fun discardUntilDelimiterImplMemory(buffer: Buffer, delimiter: Byte): Int {
    val start = buffer.readPosition
    var i = start
    val limit = buffer.writePosition
    val memory = buffer.memory

    while (i < limit) {
        if (memory[i] == delimiter) break
        i++
    }

    buffer.discardUntilIndex(i)
    return i - start
}

internal expect fun Buffer.discardUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte): Int

internal fun discardUntilDelimitersImplMemory(buffer: Buffer, delimiter1: Byte, delimiter2: Byte): Int {
    val start = buffer.readPosition
    var i = start
    val limit = buffer.writePosition
    val memory = buffer.memory

    while (i < limit) {
        val v = memory[i]
        if (v == delimiter1 || v == delimiter2) break
        i++
    }

    buffer.discardUntilIndex(i)
    return i - start
}

internal expect fun Buffer.readUntilDelimiterImpl(
    delimiter: Byte,
                                                    dst: ByteArray, offset: Int, length: Int): Int

internal expect fun Buffer.readUntilDelimitersImpl(
    delimiter1: Byte, delimiter2: Byte,
    dst: ByteArray, offset: Int, length: Int): Int

internal expect fun Buffer.readUntilDelimiterImpl(
    delimiter: Byte,
    dst: Output): Int

internal expect fun Buffer.readUntilDelimitersImpl(
    delimiter1: Byte, delimiter2: Byte,
    dst: Output): Int

internal inline fun Buffer.copyUntil(predicate: (Byte) -> Boolean, dst: ByteArray, offset: Int, length: Int): Int {
    val readPosition = readPosition
    var end = minOf(writePosition, readPosition + length)
    val memory = memory
    for (index in readPosition until end) {
        if (predicate(memory.loadAt(index))) {
            end = index
            break
        }
    }

    val copySize = end - readPosition
    memory.loadByteArray(readPosition, dst, offset, copySize)
    return copySize
}

internal inline fun Buffer.copyUntil(predicate: (Byte) -> Boolean, dst: Output): Int {
    var index = readPosition
    val end = writePosition
    val memory = memory
    do {
        if (index == end || predicate(memory.loadAt(index))) {
            break
        }
        index++
    } while (true)

    val size = index - readPosition
    dst.writeFully(this, size)
    return size
}

