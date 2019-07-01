@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

@ExperimentalUnsignedTypes
inline fun Input.readUByte(): UByte = readByte().toUByte()

@ExperimentalUnsignedTypes
inline fun Input.readUShort(): UShort = readShort().toUShort()

@ExperimentalUnsignedTypes
inline fun Input.readUInt(): UInt = readInt().toUInt()

@ExperimentalUnsignedTypes
inline fun Input.readULong(): ULong = readLong().toULong()

@ExperimentalUnsignedTypes
inline fun Input.readFully(dst: UByteArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Input.readFully(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Input.readFully(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Input.readFully(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asLongArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Output.writeUByte(v: UByte) {
    writeByte(v.toByte())
}

@ExperimentalUnsignedTypes
inline fun Output.writeUShort(v: UShort) {
    writeShort(v.toShort())
}

@ExperimentalUnsignedTypes
inline fun Output.writeUInt(v: UInt) {
    writeInt(v.toInt())
}

@ExperimentalUnsignedTypes
inline fun Output.writeULong(v: ULong) {
    writeLong(v.toLong())
}

@ExperimentalUnsignedTypes
inline fun Output.writeFully(array: UByteArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Output.writeFully(array: UShortArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Output.writeFully(array: UIntArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
inline fun Output.writeFully(array: ULongArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asLongArray(), offset, length)
}
