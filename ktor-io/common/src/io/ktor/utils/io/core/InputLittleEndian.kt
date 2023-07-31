@file:Suppress("Duplicates")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*

@Suppress("DEPRECATION")
public fun Input.readShort(byteOrder: ByteOrder): Short =
    readPrimitiveTemplate(byteOrder, { readShort() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readInt(byteOrder: ByteOrder): Int =
    readPrimitiveTemplate(byteOrder, { readInt() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readLong(byteOrder: ByteOrder): Long =
    readPrimitiveTemplate(byteOrder, { readLong() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readFloat(byteOrder: ByteOrder): Float =
    readPrimitiveTemplate(byteOrder, { readFloat() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readDouble(byteOrder: ByteOrder): Double =
    readPrimitiveTemplate(byteOrder, { readDouble() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readShortLittleEndian(): Short = readPrimitiveTemplate({ readShort() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readIntLittleEndian(): Int = readPrimitiveTemplate({ readInt() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readLongLittleEndian(): Long = readPrimitiveTemplate({ readLong() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readFloatLittleEndian(): Float = readPrimitiveTemplate({ readFloat() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Input.readDoubleLittleEndian(): Double = readPrimitiveTemplate({ readDouble() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Buffer.readShortLittleEndian(): Short = readPrimitiveTemplate({ readShort() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Buffer.readIntLittleEndian(): Int = readPrimitiveTemplate({ readInt() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Buffer.readLongLittleEndian(): Long = readPrimitiveTemplate({ readLong() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Buffer.readFloatLittleEndian(): Float = readPrimitiveTemplate({ readFloat() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
public fun Buffer.readDoubleLittleEndian(): Double = readPrimitiveTemplate({ readDouble() }, { reverseByteOrder() })

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readFullyLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asShortArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Input.readFullyLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readFullyLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asIntArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Input.readFullyLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readFullyLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asLongArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Input.readFullyLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
public fun Input.readFullyLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
public fun Input.readFullyLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readAvailableLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asShortArray(), offset, length)
}

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readAvailableLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asIntArray(), offset, length)
}

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Input.readAvailableLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asLongArray(), offset, length)
}

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readFullyLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asShortArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Buffer.readFullyLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readFullyLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asIntArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Buffer.readFullyLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readFullyLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFullyLittleEndian(dst.asLongArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Buffer.readFullyLittleEndian(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
public fun Buffer.readFullyLittleEndian(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
public fun Buffer.readFullyLittleEndian(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst, offset, length)
    val lastIndex = offset + length - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readAvailableLittleEndian(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asShortArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Buffer.readAvailableLittleEndian(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    val lastIndex = offset + result - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
    return result
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readAvailableLittleEndian(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asIntArray(), offset, length)
}

@Suppress("DEPRECATION")
public fun Buffer.readAvailableLittleEndian(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    val result = readAvailable(dst, offset, length)
    val lastIndex = offset + result - 1
    for (index in offset..lastIndex) {
        dst[index] = dst[index].reverseByteOrder()
    }
    return result
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
public fun Buffer.readAvailableLittleEndian(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailableLittleEndian(dst.asLongArray(), offset, length)
}

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
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
