package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

public suspend inline fun ByteReadChannel.readShort(byteOrder: ByteOrder): Short {
    return readShort().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readInt(byteOrder: ByteOrder): Int {
    return readInt().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readLong(byteOrder: ByteOrder): Long {
    return readLong().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readFloat(byteOrder: ByteOrder): Float {
    return readFloat().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readDouble(byteOrder: ByteOrder): Double {
    return readDouble().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readShortLittleEndian(): Short {
    return toLittleEndian(readShort()) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readIntLittleEndian(): Int {
    return toLittleEndian(readInt()) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readLongLittleEndian(): Long {
    return toLittleEndian(readLong()) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readFloatLittleEndian(): Float {
    return toLittleEndian(readFloat()) { reverseByteOrder() }
}

public suspend inline fun ByteReadChannel.readDoubleLittleEndian(): Double {
    return toLittleEndian(readDouble()) { reverseByteOrder() }
}

public suspend fun ByteWriteChannel.writeShort(value: Short, byteOrder: ByteOrder) {
    writeShort(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeInt(value: Int, byteOrder: ByteOrder) {
    writeInt(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeLong(value: Long, byteOrder: ByteOrder) {
    writeLong(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeFloat(value: Float, byteOrder: ByteOrder) {
    writeFloat(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeDouble(value: Double, byteOrder: ByteOrder) {
    writeDouble(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeShortLittleEndian(value: Short) {
    writeShort(toLittleEndian(value) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeIntLittleEndian(value: Int) {
    writeInt(toLittleEndian(value) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeLongLittleEndian(value: Long) {
    writeLong(toLittleEndian(value) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeFloatLittleEndian(value: Float) {
    writeFloat(toLittleEndian(value) { reverseByteOrder() })
}

public suspend fun ByteWriteChannel.writeDoubleLittleEndian(value: Double) {
    writeDouble(toLittleEndian(value) { reverseByteOrder() })
}

@PublishedApi
@Suppress("DEPRECATION_ERROR")
internal inline fun <T> ByteReadChannel.toLittleEndian(value: T, reverseBlock: T.() -> T): T = value.reverseBlock()

@Suppress("DEPRECATION_ERROR")
private inline fun <T> ByteWriteChannel.toLittleEndian(value: T, reverseBlock: T.() -> T): T = value.reverseBlock()

@PublishedApi
internal inline fun <T> T.reverseIfNeeded(byteOrder: ByteOrder, reverseBlock: T.() -> T): T {
    return when (byteOrder) {
        ByteOrder.BIG_ENDIAN -> this
        else -> reverseBlock()
    }
}
