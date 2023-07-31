@file:Suppress("NOTHING_TO_INLINE")

package io.ktor.utils.io.core

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readUByte(): UByte = readByte().toUByte()

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readUShort(): UShort = readShort().toUShort()

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readUInt(): UInt = readInt().toUInt()

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readULong(): ULong = readLong().toULong()

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readFully(dst: UByteArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readFully(dst: UShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readFully(dst: UIntArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Input.readFully(dst: ULongArray, offset: Int = 0, length: Int = dst.size - offset) {
    readFully(dst.asLongArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeUByte(v: UByte) {
    writeByte(v.toByte())
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeUShort(v: UShort) {
    writeShort(v.toShort())
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeUInt(v: UInt) {
    writeInt(v.toInt())
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeULong(v: ULong) {
    writeLong(v.toLong())
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeFully(array: UByteArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asByteArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeFully(array: UShortArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asShortArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeFully(array: UIntArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asIntArray(), offset, length)
}

@ExperimentalUnsignedTypes
@Suppress("DEPRECATION")
public inline fun Output.writeFully(array: ULongArray, offset: Int = 0, length: Int = array.size - offset) {
    writeFully(array.asLongArray(), offset, length)
}
