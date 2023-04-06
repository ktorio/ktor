/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

@Suppress("DEPRECATION")
internal actual fun Buffer.discardUntilDelimiterImpl(delimiter: Byte): Int {
    val content = memory.data
    var idx = readPosition
    val end = writePosition

    while (idx < end) {
        if (content[idx] == delimiter) break
        idx++
    }

    val start = readPosition
    discardUntilIndex(idx)
    return idx - start
}

@Suppress("DEPRECATION")
internal actual fun Buffer.discardUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte): Int {
    val content = memory.data
    var idx = readPosition
    val end = writePosition

    while (idx < end) {
        val v = content[idx]
        if (v == delimiter1 || v == delimiter2) break
        idx++
    }

    val start = readPosition
    discardUntilIndex(idx)
    return idx - start
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimiterImpl(
    delimiter: Byte,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    check(offset >= 0)
    check(length >= 0)
    check(offset + length <= dst.size)

    return readUntilImpl({ it == delimiter }, dst, offset, length)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimitersImpl(
    delimiter1: Byte,
    delimiter2: Byte,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    check(offset >= 0)
    check(length >= 0)
    check(offset + length <= dst.size)
    check(delimiter1 != delimiter2)

    return readUntilImpl({ it == delimiter1 || it == delimiter2 }, dst, offset, length)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimiterImpl(delimiter: Byte, dst: Output): Int {
    return readUntilImpl({ it == delimiter }, dst)
}

@Suppress("DEPRECATION")
internal actual fun Buffer.readUntilDelimitersImpl(delimiter1: Byte, delimiter2: Byte, dst: Output): Int {
    check(delimiter1 != delimiter2)

    return readUntilImpl({ it == delimiter1 || it == delimiter2 }, dst)
}

@Suppress("DEPRECATION")
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: ByteArray,
    offset: Int,
    length: Int
): Int {
    val array = memory.data
    val start = readPosition
    var i = start
    val end = i + minOf(length, readRemaining)

    while (i < end) {
        if (predicate(array[i])) break
        i++
    }

    val copied = i - start
    array.copyInto(dst, offset, start, end)
    discardUntilIndex(i)

    return copied
}

@Suppress("DEPRECATION")
private inline fun Buffer.readUntilImpl(
    predicate: (Byte) -> Boolean,
    dst: Output
): Int {
    val array = memory.data
    var i = readPosition
    var copiedTotal = 0

    dst.writeWhile { chunk ->
        chunk.writeFully(chunk, 0)
        val start = i
        val end = minOf(i + chunk.writeRemaining, writePosition)

        while (i < end) {
            if (predicate(array[i])) break
            i++
        }

        val size = i - start

        array.copyInto(chunk.memory.data, chunk.writePosition, start, i)

        chunk.commitWritten(size)
        copiedTotal += size

        chunk.writeRemaining == 0 && i < end
    }

    discardUntilIndex(i)
    return copiedTotal
}
