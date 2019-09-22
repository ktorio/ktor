package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

suspend inline fun ByteReadChannel.readShort(byteOrder: ByteOrder): Short {
    return readShort().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readInt(byteOrder: ByteOrder): Int {
    return readInt().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readLong(byteOrder: ByteOrder): Long {
    return readLong().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readFloat(byteOrder: ByteOrder): Float {
    return readFloat().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readDouble(byteOrder: ByteOrder): Double {
    return readDouble().reverseIfNeeded(byteOrder) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readShortLittleEndian(): Short {
    return toLittleEndian(readShort()) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readIntLittleEndian(): Int {
    return toLittleEndian(readInt()) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readLongLittleEndian(): Long {
    return toLittleEndian(readLong()) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readFloatLittleEndian(): Float {
    return toLittleEndian(readFloat()) { reverseByteOrder() }
}

suspend inline fun ByteReadChannel.readDoubleLittleEndian(): Double {
    return toLittleEndian(readDouble()) { reverseByteOrder() }
}

suspend fun ByteWriteChannel.writeShort(value: Short, byteOrder: ByteOrder) {
    writeShort(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeInt(value: Int, byteOrder: ByteOrder) {
    writeInt(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeLong(value: Long, byteOrder: ByteOrder) {
    writeLong(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeFloat(value: Float, byteOrder: ByteOrder) {
    writeFloat(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeDouble(value: Double, byteOrder: ByteOrder) {
    writeDouble(value.reverseIfNeeded(byteOrder) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeShortLittleEndian(value: Short) {
    writeShort(toLittleEndian(value) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeIntLittleEndian(value: Int) {
    writeInt(toLittleEndian(value) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeLongLittleEndian(value: Long) {
    writeLong(toLittleEndian(value) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeFloatLittleEndian(value: Float) {
    writeFloat(toLittleEndian(value) { reverseByteOrder() })
}

suspend fun ByteWriteChannel.writeDoubleLittleEndian(value: Double) {
    writeDouble(toLittleEndian(value) { reverseByteOrder() })
}

@PublishedApi
@Suppress("DEPRECATION_ERROR")
internal inline fun <T> ByteReadChannel.toLittleEndian(value: T, reverseBlock: T.() -> T): T {
    return when (readByteOrder) {
        ByteOrder.LITTLE_ENDIAN -> value
        else -> value.reverseBlock()
    }
}

@Suppress("DEPRECATION_ERROR")
private inline fun <T> ByteWriteChannel.toLittleEndian(value: T, reverseBlock: T.() -> T): T {
    return when (writeByteOrder) {
        ByteOrder.LITTLE_ENDIAN -> value
        else -> value.reverseBlock()
    }
}

@PublishedApi
internal inline fun <T> T.reverseIfNeeded(byteOrder: ByteOrder, reverseBlock: T.() -> T): T {
    return when (byteOrder) {
        ByteOrder.BIG_ENDIAN -> this
        else -> reverseBlock()
    }
}
