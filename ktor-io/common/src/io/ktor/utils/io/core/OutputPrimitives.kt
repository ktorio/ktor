@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*

fun Output.writeShort(value: Short) {
    if (!writePrimitiveTemplate(2) { memory, index -> memory.storeShortAt(index, value) }) {
        writeShortFallback(value)
    }
}

private fun Output.writeShortFallback(value: Short) {
    if (!writePrimitiveFallbackTemplate(2) { it.writeShort(value) }) {
        writeByte(value.highByte)
        writeByte(value.lowByte)
    }
}

fun Output.writeInt(value: Int) {
    if (!writePrimitiveTemplate(4) { memory, index -> memory.storeIntAt(index, value) }) {
        writeIntFallback(value)
    }
}

private fun Output.writeIntFallback(value: Int) {
    if (!writePrimitiveFallbackTemplate(4) { it.writeInt(value) }) {
        writeIntByteByByte(value)
    }
}

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

fun Output.writeLong(value: Long) {
    if (!writePrimitiveTemplate(8) { memory, index -> memory.storeLongAt(index, value) }) {
        writeLongFallback(value)
    }
}

private fun Output.writeLongFallback(value: Long) {
    if (!writePrimitiveFallbackTemplate(8) { it.writeLong(value) }) {
        writeIntByteByByte(value.highInt)
        writeIntByteByByte(value.lowInt)
    }
}

fun Output.writeFloat(value: Float) {
    if (!writePrimitiveTemplate(4) { memory, index -> memory.storeFloatAt(index, value) }) {
        writeIntFallback(value.toRawBits())
    }
}

fun Output.writeDouble(value: Double) {
    if (!writePrimitiveTemplate(8) { memory, index -> memory.storeDoubleAt(index, value) }) {
        writeLongFallback(value.toRawBits())
    }
}

private inline fun Output.writePrimitiveTemplate(
    componentSize: Int,
    block: (Memory, index: Int) -> Unit
): Boolean {
    if (this is AbstractOutput) {
        val index = tailPosition
        if (tailEndExclusive - index > componentSize) {
            tailPosition = index + componentSize
            block(tailMemory, index)
            return true
        }
    }

    return false
}

private inline fun Output.writePrimitiveFallbackTemplate(
    componentSize: Int,
    writeOperation: (Buffer) -> Unit
): Boolean {
    if (this is AbstractOutput) {
        val tail = prepareWriteHead(componentSize)
        writeOperation(tail)
        afterHeadWrite()
        return true
    }

    return false
}
