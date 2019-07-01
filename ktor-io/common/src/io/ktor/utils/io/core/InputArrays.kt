package io.ktor.utils.io.core

import io.ktor.utils.io.bits.Memory

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyBytesTemplate(offset, length) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyTemplate(offset, length, 2) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyTemplate(offset, length, 4) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyTemplate(offset, length, 8) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyTemplate(offset, length, 4) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyTemplate(offset, length, 8) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }.requireNoRemaining()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: Buffer, length: Int = dst.writeRemaining) {
    readFullyBytesTemplate(0, length) { src, _, count ->
        src.readFully(dst, count)
    }.requireNoRemaining()
}

fun Input.readFully(destination: Memory, destinationOffset: Int, length: Int) {
    readFully(destination, destinationOffset.toLong(), length.toLong())
}

fun Input.readFully(destination: Memory, destinationOffset: Long, length: Long) {
    if (readAvailable(destination, destinationOffset, length) != length) {
        prematureEndOfStream(length)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyBytesTemplate(offset, length) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyTemplate(offset, length, 2) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyTemplate(offset, length, 4) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyTemplate(offset, length, 8) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyTemplate(offset, length, 4) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return length - readFullyTemplate(offset, length, 8) { src, dstOffset, count ->
        src.readFully(dst, dstOffset, count)
    }
}

fun Input.readAvailable(dst: Buffer, length: Int = dst.writeRemaining): Int {
    return length - readFullyBytesTemplate(0, length) { src, _, count ->
        src.readFully(dst, count)
    }
}

fun Input.readAvailable(destination: Memory, destinationOffset: Int, length: Int): Int {
    return readAvailable(destination, destinationOffset.toLong(), length.toLong()).toInt()
}

fun Input.readAvailable(destination: Memory, destinationOffset: Long, length: Long): Long {
    val remaining = readFullyBytesTemplate(destinationOffset, length) { src, srcOffset, dstOffset, count ->
        src.copyTo(destination, srcOffset, count.toLong(), dstOffset)
    }
    val result = length - remaining
    return when {
        result == 0L && endOfInput -> -1
        else -> result
    }
}

/**
 * @return number of bytes remaining or 0 if all [length] bytes were copied
 */
private inline fun Input.readFullyBytesTemplate(
    initialDstOffset: Int,
    length: Int,
    readBlock: (src: Buffer, dstOffset: Int, count: Int) -> Unit
): Int {
    var remaining = length
    var dstOffset = initialDstOffset

    takeWhile { buffer ->
        val count = minOf(remaining, buffer.readRemaining)
        readBlock(buffer, dstOffset, count)
        remaining -= count
        dstOffset += count

        remaining > 0
    }

    return remaining
}

/**
 * @return number of bytes remaining or 0 if all [length] bytes were copied
 */
private inline fun Input.readFullyBytesTemplate(
    initialDstOffset: Long,
    length: Long,
    readBlock: (src: Memory, srcOffset: Long, dstOffset: Long, count: Int) -> Unit
): Long {
    var remaining = length
    var dstOffset = initialDstOffset

    takeWhile { buffer ->
        val count = minOf(remaining, buffer.readRemaining.toLong()).toInt()
        readBlock(buffer.memory, buffer.readPosition.toLong(), dstOffset, count)
        buffer.discardExact(count)
        remaining -= count
        dstOffset += count

        remaining > 0
    }

    return remaining
}

/**
 * @return number of elements remaining or 0 if all [length] elements were copied
 */
private inline fun Input.readFullyTemplate(
    offset: Int,
    length: Int,
    componentSize: Int,
    readBlock: (src: Buffer, dstOffset: Int, count: Int) -> Unit
): Int {
    var remaining = length
    var dstOffset = offset

    takeWhileSize { buffer ->
        val count = minOf(remaining, buffer.readRemaining / componentSize)
        readBlock(buffer, dstOffset, count)
        remaining -= count
        dstOffset += count

        when {
            remaining > 0 -> componentSize
            else -> 0
        }
    }

    return remaining
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.requireNoRemaining() {
    if (this > 0) {
        prematureEndOfStream(this)
    }
}
