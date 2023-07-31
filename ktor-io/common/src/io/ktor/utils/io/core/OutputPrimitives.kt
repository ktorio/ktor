package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*

@Suppress("DEPRECATION")
public fun Output.writeShort(value: Short) {
    if (!writePrimitiveTemplate(2) { memory, index -> memory.storeShortAt(index, value) }) {
        writeShortFallback(value)
    }
}

@Suppress("DEPRECATION")
private fun Output.writeShortFallback(value: Short) {
    if (!writePrimitiveFallbackTemplate(2) { it.writeShort(value) }) {
        writeByte(value.highByte)
        writeByte(value.lowByte)
    }
}

@Suppress("DEPRECATION")
public fun Output.writeInt(value: Int) {
    if (!writePrimitiveTemplate(4) { memory, index -> memory.storeIntAt(index, value) }) {
        writeIntFallback(value)
    }
}

@Suppress("DEPRECATION")
private fun Output.writeIntFallback(value: Int) {
    if (!writePrimitiveFallbackTemplate(4) { it.writeInt(value) }) {
        writeIntByteByByte(value)
    }
}

@Suppress("DEPRECATION")
private fun Output.writeIntByteByByte(value: Int) {
    value.highShort.let {
        writeByte(it.highByte)
        writeByte(it.lowByte)
    }
    value.lowShort.let {
        writeByte(it.highByte)
        writeByte(it.lowByte)
    }
}

@Suppress("DEPRECATION")
public fun Output.writeLong(value: Long) {
    if (!writePrimitiveTemplate(8) { memory, index -> memory.storeLongAt(index, value) }) {
        writeLongFallback(value)
    }
}

@Suppress("DEPRECATION")
private fun Output.writeLongFallback(value: Long) {
    if (!writePrimitiveFallbackTemplate(8) { it.writeLong(value) }) {
        writeIntByteByByte(value.highInt)
        writeIntByteByByte(value.lowInt)
    }
}

@Suppress("DEPRECATION")
public fun Output.writeFloat(value: Float) {
    if (!writePrimitiveTemplate(4) { memory, index -> memory.storeFloatAt(index, value) }) {
        writeIntFallback(value.toRawBits())
    }
}

@Suppress("DEPRECATION")
public fun Output.writeDouble(value: Double) {
    if (!writePrimitiveTemplate(8) { memory, index -> memory.storeDoubleAt(index, value) }) {
        writeLongFallback(value.toRawBits())
    }
}

@Suppress("DEPRECATION")
private inline fun Output.writePrimitiveTemplate(
    componentSize: Int,
    block: (Memory, index: Int) -> Unit
): Boolean {
    val index = tailPosition
    if (tailEndExclusive - index > componentSize) {
        tailPosition = index + componentSize
        block(tailMemory, index)
        return true
    }

    return false
}

@Suppress("DEPRECATION")
private inline fun Output.writePrimitiveFallbackTemplate(
    componentSize: Int,
    writeOperation: (Buffer) -> Unit
): Boolean {
    val tail = prepareWriteHead(componentSize)
    writeOperation(tail)
    afterHeadWrite()
    return true
}
