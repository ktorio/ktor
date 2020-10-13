@file:Suppress("MoveLambdaOutsideParentheses")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*


public fun Output.writeShort(value: Short, byteOrder: ByteOrder) {
    writePrimitiveTemplate(value, byteOrder, { writeShort(it) }, { reverseByteOrder() })
}

public fun Output.writeInt(value: Int, byteOrder: ByteOrder) {
    writePrimitiveTemplate(value, byteOrder, { writeInt(it) }, { reverseByteOrder() })
}

public fun Output.writeLong(value: Long, byteOrder: ByteOrder) {
    writePrimitiveTemplate(value, byteOrder, { writeLong(it) }, { reverseByteOrder() })
}

public fun Output.writeFloat(value: Float, byteOrder: ByteOrder) {
    writePrimitiveTemplate(value, byteOrder, { writeFloat(it) }, { reverseByteOrder() })
}

public fun Output.writeDouble(value: Double, byteOrder: ByteOrder) {
    writePrimitiveTemplate(value, byteOrder, { writeDouble(it) }, { reverseByteOrder() })
}

public fun Output.writeShortLittleEndian(value: Short) {
    writePrimitiveTemplate(value, { writeShort(it) }, { reverseByteOrder() })
}

public fun Output.writeIntLittleEndian(value: Int) {
    writePrimitiveTemplate(value, { writeInt(it) }, { reverseByteOrder() })
}

public fun Output.writeLongLittleEndian(value: Long) {
    writePrimitiveTemplate(value, { writeLong(it) }, { reverseByteOrder() })
}

public fun Output.writeFloatLittleEndian(value: Float) {
    writePrimitiveTemplate(value, { writeFloat(it) }, { reverseByteOrder() })
}

public fun Output.writeDoubleLittleEndian(value: Double) {
    writePrimitiveTemplate(value, { writeDouble(it) }, { reverseByteOrder() })
}

public fun Output.writeFullyLittleEndian(source: UShortArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asShortArray(), offset, length)
}

public fun Buffer.writeShortLittleEndian(value: Short) {
    writePrimitiveTemplate(value, { writeShort(it) }, { reverseByteOrder() })
}

public fun Buffer.writeIntLittleEndian(value: Int) {
    writePrimitiveTemplate(value, { writeInt(it) }, { reverseByteOrder() })
}

public fun Buffer.writeLongLittleEndian(value: Long) {
    writePrimitiveTemplate(value, { writeLong(it) }, { reverseByteOrder() })
}

public fun Buffer.writeFloatLittleEndian(value: Float) {
    writePrimitiveTemplate(value, { writeFloat(it) }, { reverseByteOrder() })
}

public fun Buffer.writeDoubleLittleEndian(value: Double) {
    writePrimitiveTemplate(value, { writeDouble(it) }, { reverseByteOrder() })
}

public fun Buffer.writeFullyLittleEndian(source: UShortArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asShortArray(), offset, length)
}

public fun Output.writeFullyLittleEndian(source: ShortArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        2,
        { writeShort(source[it].reverseByteOrder()) })
}

public fun Output.writeFullyLittleEndian(source: UIntArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asIntArray(), offset, length)
}

public fun Output.writeFullyLittleEndian(source: IntArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        4,
        { writeInt(source[it].reverseByteOrder()) })
}

public fun Output.writeFullyLittleEndian(source: ULongArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asLongArray(), offset, length)
}

public fun Output.writeFullyLittleEndian(source: LongArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        8,
        { writeLong(source[it].reverseByteOrder()) })
}

public fun Output.writeFullyLittleEndian(source: FloatArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        4,
        { writeFloat(source[it].reverseByteOrder()) })
}

public fun Output.writeFullyLittleEndian(source: DoubleArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        8,
        { writeDouble(source[it].reverseByteOrder()) })
}

public fun Buffer.writeFullyLittleEndian(source: ShortArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        2,
        { writeShort(source[it].reverseByteOrder()) })
}

public fun Buffer.writeFullyLittleEndian(source: UIntArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asIntArray(), offset, length)
}

public fun Buffer.writeFullyLittleEndian(source: IntArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        4,
        { writeInt(source[it].reverseByteOrder()) })
}

public fun Buffer.writeFullyLittleEndian(source: ULongArray, offset: Int = 0, length: Int = source.size - offset) {
    writeFullyLittleEndian(source.asLongArray(), offset, length)
}

public fun Buffer.writeFullyLittleEndian(source: LongArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        8,
        { writeLong(source[it].reverseByteOrder()) })
}

public fun Buffer.writeFullyLittleEndian(source: FloatArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        4,
        { writeFloat(source[it].reverseByteOrder()) })
}

public fun Buffer.writeFullyLittleEndian(source: DoubleArray, offset: Int = 0, length: Int = source.size - offset) {
    writeArrayTemplate(
        offset,
        length,
        8,
        { writeDouble(source[it].reverseByteOrder()) })
}

private inline fun <T : Any> writePrimitiveTemplate(value: T, write: (T) -> Unit, reverse: T.() -> T) {
    write(value.reverse())
}

private inline fun <T : Any> writePrimitiveTemplate(
    value: T,
    byteOrder: ByteOrder,
    write: (T) -> Unit,
    reverse: T.() -> T
) {
    write(
        when (byteOrder) {
            ByteOrder.BIG_ENDIAN -> value
            else -> value.reverse()
        }
    )
}

private inline fun Output.writeArrayTemplate(
    offset: Int,
    length: Int,
    componentSize: Int,
    writeComponent: Buffer.(Int) -> Unit
) {
    val untilIndex = offset + length
    var start = offset
    writeWhileSize(componentSize) { buffer ->
        val size = minOf(buffer.writeRemaining / componentSize, untilIndex - start)
        val lastIndex = start + size - 1
        for (index in start..lastIndex) {
            writeComponent(buffer, index)
        }
        start += size
        when {
            start < untilIndex -> componentSize
            else -> 0
        }
    }
}

private inline fun Buffer.writeArrayTemplate(
    offset: Int,
    length: Int,
    componentSize: Int,
    writeComponent: Buffer.(Int) -> Unit
) {
    val untilIndex = offset + length
    var start = offset
    val buffer = this

    val size = minOf(buffer.writeRemaining / componentSize, untilIndex - start)
    val lastIndex = start + size - 1
    for (index in start..lastIndex) {
        writeComponent(buffer, index)
    }
    start += size
}
