@file:Suppress("Duplicates")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*

public fun Input.readShort(byteOrder: ByteOrder): Short =
    readPrimitiveTemplate(byteOrder, { readShort() }, { reverseByteOrder() })

public fun Input.readInt(byteOrder: ByteOrder): Int =
    readPrimitiveTemplate(byteOrder, { readInt() }, { reverseByteOrder() })

public fun Input.readLong(byteOrder: ByteOrder): Long =
    readPrimitiveTemplate(byteOrder, { readLong() }, { reverseByteOrder() })

public fun Input.readFloat(byteOrder: ByteOrder): Float =
    readPrimitiveTemplate(byteOrder, { readFloat() }, { reverseByteOrder() })

public fun Input.readDouble(byteOrder: ByteOrder): Double =
    readPrimitiveTemplate(byteOrder, { readDouble() }, { reverseByteOrder() })

public fun Input.readShortLittleEndian(): Short = readPrimitiveTemplate({ readShort() }, { reverseByteOrder() })

public fun Input.readIntLittleEndian(): Int = readPrimitiveTemplate({ readInt() }, { reverseByteOrder() })

public fun Input.readLongLittleEndian(): Long = readPrimitiveTemplate({ readLong() }, { reverseByteOrder() })

public fun Input.readFloatLittleEndian(): Float = readPrimitiveTemplate({ readFloat() }, { reverseByteOrder() })

public fun Input.readDoubleLittleEndian(): Double = readPrimitiveTemplate({ readDouble() }, { reverseByteOrder() })

public fun Buffer.readShortLittleEndian(): Short = readPrimitiveTemplate({ readShort() }, { reverseByteOrder() })

public fun Buffer.readIntLittleEndian(): Int = readPrimitiveTemplate({ readInt() }, { reverseByteOrder() })

public fun Buffer.readLongLittleEndian(): Long = readPrimitiveTemplate({ readLong() }, { reverseByteOrder() })

public fun Buffer.readFloatLittleEndian(): Float = readPrimitiveTemplate({ readFloat() }, { reverseByteOrder() })

public fun Buffer.readDoubleLittleEndian(): Double = readPrimitiveTemplate({ readDouble() }, { reverseByteOrder() })

public fun Input.readFullyLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asShortArray(), offset, length)
}

public fun Input.readFullyLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Input.readFullyLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asIntArray(), offset, length)
}

public fun Input.readFullyLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Input.readFullyLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asLongArray(), offset, length)
}

public fun Input.readFullyLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Input.readFullyLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Input.readFullyLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Input.readAvailableLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asShortArray(), offset, length)
}

public fun Input.readAvailableLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Input.readAvailableLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asIntArray(), offset, length)
}

public fun Input.readAvailableLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Input.readAvailableLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asLongArray(), offset, length)
}

public fun Input.readAvailableLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Input.readAvailableLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Input.readAvailableLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Buffer.readFullyLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asShortArray(), offset, length)
}

public fun Buffer.readFullyLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Buffer.readFullyLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asIntArray(), offset, length)
}

public fun Buffer.readFullyLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Buffer.readFullyLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asLongArray(), offset, length)
}

public fun Buffer.readFullyLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Buffer.readFullyLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Buffer.readFullyLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

public fun Buffer.readAvailableLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asShortArray(), offset, length)
}

public fun Buffer.readAvailableLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    val lastIndex = offset + result - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
    return result
}

public fun Buffer.readAvailableLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asIntArray(), offset, length)
}

public fun Buffer.readAvailableLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    val lastIndex = offset + result - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
    return result
}

public fun Buffer.readAvailableLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asLongArray(), offset, length)
}

public fun Buffer.readAvailableLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Buffer.readAvailableLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

public fun Buffer.readAvailableLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    if (result > 0) {
        val lastIndex = offset + result - 1
        for (index in offset..lastIndex) {
            dst[index] = dst[index].reverseByteOrder()
        }
    }
    return result
}

private inline fun <T : Any> readPrimitiveTemplate(read: () -> T, reverse: T.() -> T): T {
    return read().reverse()
}

private inline fun <T : Any> readPrimitiveTemplate(
    byteOrder: ByteOrder,
    read: () -> T,
    reverse: T.() -> T
): T {
    return when (byteOrder) {
        ByteOrder.BIG_ENDIAN -> read()
        else -> read().reverse()
    }
}
