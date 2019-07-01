package io.ktor.utils.io.core

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.Memory

@Suppress("NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual interface Input : Closeable {
    @Deprecated(
        "Not supported anymore. All operations are big endian by default. " +
            "Use readXXXLittleEndian or readXXX then X.reverseByteOrder() instead.",
        level = DeprecationLevel.ERROR
    )
    actual var byteOrder: ByteOrder

    /**
     * It is `true` when it is known that no more bytes will be available. When it is `false` then this means that
     * it is not known yet or there are available bytes.
     * Please note that `false` value doesn't guarantee that there are available bytes so `readByte()` may fail.
     */
    actual val endOfInput: Boolean

    /**
     * Copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
     * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes then
     * it fails with an exception.
     * It is safe to specify `max > destination.writeRemaining` but
     * `min` shouldn't be bigger than the [destination] free space.
     * This function could trigger the underlying source reading that may lead to blocking I/O.
     * It is safe to specify too big [offset] but only if `min = 0`, fails otherwise.
     * This function usually copy more bytes than [min] (unless `max = min`).
     *
     * @param destination to write bytes
     * @param offset to skip input
     * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
     * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
     * @return number of bytes copied to the [destination] possibly `0`
     * @throws Throwable when not enough bytes available to provide
     */
    actual fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long,
        min: Long,
        max: Long
    ): Long

    /**
     * Read the next upcoming byte
     * @throws EOFException if no more bytes available.
     */
    actual fun readByte(): Byte

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readShort(): Short {
        return readShort()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readInt(): Int {
        return readInt()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readLong(): Long {
        return readLong()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFloat(): Float {
        return readFloat()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readDouble(): Double {
        return readDouble()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: ByteArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: ShortArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: IntArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: LongArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: FloatArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readFully(dst: DoubleArray, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT", "DEPRECATION")
    actual fun readFully(dst: IoBuffer, length: Int) {
        return readFully(dst, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: IntArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: LongArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT")
    actual fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("ACTUAL_WITHOUT_EXPECT", "DEPRECATION")
    actual fun readAvailable(dst: IoBuffer, length: Int): Int {
        return readAvailable(dst, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: CPointer<ByteVar>, offset: Int, length: Int) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        return readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: CPointer<ByteVar>, offset: Long, length: Long): Long {
        return readAvailable(dst, offset, length)
    }

    /*
     * Returns next byte (unsigned) or `-1` if no more bytes available
     */
    actual fun tryPeek(): Int

    /**
     * Copy available bytes to the specified [buffer] but keep them available.
     * If the underlying implementation could trigger
     * bytes population from the underlying source and block until any bytes available
     *
     * Very similar to [readAvailable] but don't discard copied bytes.
     *
     * @return number of bytes were copied
     */
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION", "ACTUAL_WITHOUT_EXPECT")
    actual fun peekTo(buffer: IoBuffer): Int {
        return peekTo(buffer)
    }

    actual fun discard(n: Long): Long

    actual override fun close()
}
