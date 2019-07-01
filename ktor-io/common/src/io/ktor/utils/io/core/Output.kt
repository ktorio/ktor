package io.ktor.utils.io.core

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.internal.*
import kotlin.jvm.JvmName

/**
 * This shouldn't be implemented directly. Inherit [AbstractOutput] instead.
 */
expect interface Output : Appendable, Closeable {
    @Deprecated(
        "This is no longer supported. All operations are big endian by default. Use writeXXXLittleEndian " +
            "to write primitives in little endian order" +
            " or do X.reverseByteOrder() and then writeXXX instead.",
        level = DeprecationLevel.ERROR
    )
    var byteOrder: ByteOrder

    fun writeByte(v: Byte)

    fun flush()

    override fun close()

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeShort(v: Short) {
        writeShort(v)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeInt(v: Int) {
        writeInt(v)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeLong(v: Long) {
        writeLong(v)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFloat(v: Float) {
        writeFloat(v)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeDouble(v: Double) {
        writeDouble(v)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: ByteArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: ShortArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: IntArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: LongArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: FloatArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun writeFully(src: IoBuffer, length: Int) {
        writeFully(src, length)
    }

    fun append(csq: CharArray, start: Int, end: Int): Appendable

    @Suppress("EXPECTED_DECLARATION_WITH_BODY", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun fill(n: Long, v: Byte) {
        fill(n, v)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.append(csq: CharSequence, start: Int = 0, end: Int = csq.length): Appendable {
    return append(csq, start, end)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.append(csq: CharArray, start: Int = 0, end: Int = csq.size): Appendable {
    return append(csq, start, end)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: ByteArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyBytesTemplate(offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: ShortArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(2, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: IntArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(4, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: LongArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(8, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: FloatArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(4, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.writeFully(src: DoubleArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(8, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Output.writeFully(src: IoBuffer, length: Int = src.readRemaining) {
    writeFully(src as Buffer, length)
}

fun Output.writeFully(src: Buffer, length: Int = src.readRemaining) {
    writeFullyBytesTemplate(0, length) { buffer, _, count ->
        buffer.writeFully(src, count)
    }
}

fun Output.writeFully(src: Memory, offset: Int, length: Int) {
    writeFully(src, offset.toLong(), length.toLong())
}

fun Output.writeFully(src: Memory, offset: Long, length: Long) {
    writeFullyBytesTemplate(offset, length) { memory, destinationOffset, sourceOffset, count ->
        src.copyTo(memory, sourceOffset, count, destinationOffset)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Output.fill(times: Long, value: Byte = 0) {
    if (this is AbstractOutput) {
        var written = 0L
        writeWhile { buffer ->
            val partTimes = minOf(buffer.writeRemaining.toLong(), times - written).toInt()
            buffer.fill(partTimes, value)
            written += partTimes
            written < times
        }
    } else {
        fillFallback(times, value)
    }
}

private fun Output.fillFallback(times: Long, value: Byte) {
    for (iterate in 0 until times) {
        writeByte(value)
    }
}

/**
 * Append number of chunks invoking [block] function while the returned value is true.
 * Depending on the output underlying implementation it could invoke [block] function with the same buffer several times
 * however it is guaranteed that it is always non-empty.
 */
@DangerousInternalIoApi
inline fun Output.writeWhile(block: (Buffer) -> Boolean) {
    var tail: ChunkBuffer = prepareWriteHead(1, null)
    try {
        while (true) {
            if (!block(tail)) break
            tail = prepareWriteHead(1, tail)
        }
    } finally {
        afterHeadWrite(tail)
    }
}

/**
 * Append number of chunks invoking [block] function while the returned value is positive.
 * If returned value is positive then it will be invoked again with a buffer having at least requested number of
 * bytes space (could be the same buffer as before if it complies to the restriction).
 * @param initialSize for the first buffer passed to [block] function
 */
@DangerousInternalIoApi
inline fun Output.writeWhileSize(initialSize: Int = 1, block: (Buffer) -> Int) {
    var tail = prepareWriteHead(initialSize, null)

    try {
        var size: Int
        while (true) {
            size = block(tail)
            if (size <= 0) break
            tail = prepareWriteHead(size, tail)
        }
    } finally {
        afterHeadWrite(tail)
    }
}

fun Output.writePacket(packet: ByteReadPacket) {
    @Suppress("DEPRECATION_ERROR")
    if (this is BytePacketBuilderBase) {
        writePacket(packet)
        return
    }

    packet.takeWhile { from ->
        writeFully(from)
        true
    }
}

private inline fun Output.writeFullyBytesTemplate(
    offset: Int,
    length: Int,
    block: (Buffer, currentOffset: Int, count: Int) -> Unit
) {
    var currentOffset = offset
    var remaining = length

    writeWhile { buffer ->
        val size = minOf(remaining, buffer.writeRemaining)
        block(buffer, currentOffset, size)
        currentOffset += size
        remaining -= size
        remaining > 0
    }
}

private inline fun Output.writeFullyBytesTemplate(
    initialOffset: Long,
    length: Long,
    block: (destination: Memory, destinationOffset: Long, currentOffset: Long, count: Long) -> Unit
) {
    var currentOffset = initialOffset
    var remaining = length

    writeWhile { buffer ->
        val size = minOf(remaining, buffer.writeRemaining.toLong())
        block(buffer.memory, buffer.writePosition.toLong(), currentOffset, size)
        buffer.commitWritten(size.toInt())
        currentOffset += size
        remaining -= size
        remaining > 0
    }
}

private inline fun Output.writeFullyTemplate(
    componentSize: Int,
    offset: Int,
    length: Int,
    block: (Buffer, currentOffset: Int, count: Int) -> Unit
) {
    var currentOffset = offset
    var remaining = length

    writeWhileSize(componentSize) { buffer ->
        val size = minOf(remaining, buffer.writeRemaining)
        block(buffer, currentOffset, size)
        currentOffset += size
        remaining -= size
        remaining * componentSize
    }
}

