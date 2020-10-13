@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

@ExperimentalUnsignedTypes
public inline fun Input.readUByte(): UByte = readByte().toUByte()

@ExperimentalUnsignedTypes
public inline fun Input.readUShort(): UShort = readShort().toUShort()

@ExperimentalUnsignedTypes
public inline fun Input.readUInt(): UInt = readInt().toUInt()

@ExperimentalUnsignedTypes
public inline fun Input.readULong(): ULong = readLong().toULong()

@ExperimentalUnsignedTypes
public inline fun Input.readFully(dst: UByteArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Input.readFully(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Input.readFully(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Input.readFully(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asLongArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Output.writeUByte(v: UByte) {
    writeByte(v.toByte())
}

@ExperimentalUnsignedTypes
public inline fun Output.writeUShort(v: UShort) {
    writeShort(v.toShort())
}

@ExperimentalUnsignedTypes
public inline fun Output.writeUInt(v: UInt) {
    writeInt(v.toInt())
}

@ExperimentalUnsignedTypes
public inline fun Output.writeULong(v: ULong) {
    writeLong(v.toLong())
}

@ExperimentalUnsignedTypes
public inline fun Output.writeFully(array: UByteArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Output.writeFully(array: UShortArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Output.writeFully(array: UIntArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
public inline fun Output.writeFully(array: ULongArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asLongArray(), offset, length)
}
