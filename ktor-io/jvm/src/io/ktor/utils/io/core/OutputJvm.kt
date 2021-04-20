// ktlint-disable filename
package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import java.nio.*

/**
 * This shouldn't be implemented directly. Inherit [AbstractOutput] instead.
 */
@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
public actual interface Output : Closeable, Appendable {
    @Deprecated("Write with writeXXXLittleEndian or do X.reverseByteOrder() and then writeXXX instead.")
    public actual var byteOrder: ByteOrder

    public actual fun writeByte(v: Byte)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeShort(v: Short) {
        writeShort(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeInt(v: Int) {
        writeInt(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeLong(v: Long) {
        writeLong(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFloat(v: Float) {
        writeFloat(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeDouble(v: Double) {
        writeDouble(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: ByteArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: ShortArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: IntArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: LongArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: FloatArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    public actual fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT", "DEPRECATION")
    public actual fun writeFully(src: ChunkBuffer, length: Int) {
        writeFully(src, length)
    }

    public actual fun append(csq: CharArray, start: Int, end: Int): Appendable

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun writeFully(bb: ByteBuffer) {
        writeFully(bb)
    }

    @Suppress("ACTUAL_WITHOUT_EXPECT")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public actual fun fill(n: Long, v: Byte) {
        fill(n, v)
    }

    public actual fun flush()

    actual override fun close()
}
