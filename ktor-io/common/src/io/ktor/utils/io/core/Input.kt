package io.ktor.utils.io.core

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.get
import io.ktor.utils.io.core.internal.*
import kotlin.jvm.JvmName

/**
 * Usually shouldn't be implemented directly. Inherit [AbstractInput] instead.
 */
public expect interface Input : Closeable {
    @Deprecated(
        "Not supported anymore. All operations are big endian by default. " +
            "Use readXXXLittleEndian or readXXX then X.reverseByteOrder() instead.",
        level = DeprecationLevel.ERROR
    )
    public var byteOrder: ByteOrder

    /**
     * It is `true` when it is known that no more bytes will be available. When it is `false` then this means that
     * it is not known yet or there are available bytes.
     * Please note that `false` value doesn't guarantee that there are available bytes so `readByte()` may fail.
     */
    public val endOfInput: Boolean

    /**
     * Read the next upcoming byte
     * @throws EOFException if no more bytes available.
     */
    public fun readByte(): Byte

    /*
     * Returns next byte (unsigned) or `-1` if no more bytes available
     */
    public fun tryPeek(): Int

    /**
     * Try to copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
     * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes after skipping [offset]
     * bytes then it will trigger the underlying source reading first and after that will
     * simply copy available bytes even if EOF encountered so [min] is not a requirement but a desired number of bytes.
     * It is safe to specify [max] greater than the destination free space.
     * `min` shouldn't be bigger than the [destination] free space.
     * This function could trigger the underlying source reading that may lead to blocking I/O.
     * It is allowed to specify too big [offset] so in this case this function will always return `0` after prefetching
     * all underlying bytes but note that it may lead to significant memory consumption.
     * This function usually copy more bytes than [min] (unless `max = min`) but it is not guaranteed.
     * When `0` is returned with `offset = 0` then it makes sense to check [endOfInput].
     *
     * @param destination to write bytes
     * @param offset to skip input
     * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
     * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
     * @return number of bytes copied to the [destination] possibly `0`
     */
    public fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long = 0,
        min: Long = 1,
        max: Long = Long.MAX_VALUE
    ): Long

    /**
     * Discard at most [n] bytes
     */
    public fun discard(n: Long): Long

    /**
     * Close input including the underlying source. All pending bytes will be discarded.
     * It is not recommended to invoke it with read operations in-progress concurrently.
     */
    override fun close()

    /**
     * Copy available bytes to the specified [buffer] but keep them available.
     * The underlying implementation could trigger
     * bytes population from the underlying source and block until any bytes available.
     *
     * Very similar to [readAvailable] but don't discard copied bytes.
     *
     * @return number of bytes were copied
     */
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("EXPECTED_DECLARATION_WITH_BODY", "DEPRECATION")
    public fun peekTo(buffer: ChunkBuffer): Int {
        return peekTo(buffer)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readShort(): Short {
        return readShort()
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readInt(): Int {
        return readInt()
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readLong(): Long {
        return readLong()
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFloat(): Float {
        return readFloat()
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readDouble(): Double {
        return readDouble()
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: ByteArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: ShortArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: IntArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: LongArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: FloatArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: DoubleArray, offset: Int, length: Int) {
        readFully(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readFully(dst: ChunkBuffer, length: Int) {
        readFully(dst, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: IntArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: LongArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int {
        return readAvailable(dst, offset, length)
    }

    @Suppress("EXPECTED_DECLARATION_WITH_BODY", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun readAvailable(dst: ChunkBuffer, length: Int): Int {
        return readAvailable(dst, length)
    }
}

/**
 * Discard all remaining bytes.
 * @return number of bytes were discarded
 */
public fun Input.discard(): Long {
    return discard(Long.MAX_VALUE)
}

/**
 * Discard exactly [n] bytes or fail if not enough bytes in the input to be discarded.
 */
public fun Input.discardExact(n: Long) {
    val discarded = discard(n)
    if (discarded != n) {
        throw IllegalStateException("Only $discarded bytes were discarded of $n requested")
    }
}

/**
 * Discard exactly [n] bytes or fail if not enough bytes in the input to be discarded.
 */
public fun Input.discardExact(n: Int) {
    discardExact(n.toLong())
}

/**
 * Invoke [block] function for every chunk until end of input or [block] function return `false`
 * [block] function returns `true` to request more chunks or `false` to stop loop
 *
 * It is not guaranteed that every chunk will have fixed size but it will be never empty.
 * [block] function should never release provided buffer and should not write to it otherwise an undefined behaviour
 * could be observed
 */
@DangerousInternalIoApi
public inline fun Input.takeWhile(block: (Buffer) -> Boolean) {
    var release = true
    var current = prepareReadFirstHead(1) ?: return

    try {
        do {
            if (!block(current)) {
                break
            }
            release = false
            val next = prepareReadNextHead(current) ?: break
            current = next
            release = true
        } while (true)
    } finally {
        if (release) {
            completeReadHead(current)
        }
    }
}

/**
 * Invoke [block] function for every chunk until end of input or [block] function return zero
 * [block] function returns number of bytes required to read next primitive and shouldn't require too many bytes at once
 * otherwise it could fail with an exception.
 * It is not guaranteed that every chunk will have fixed size but it will be always at least requested bytes length.
 * [block] function should never release provided buffer and should not write to it otherwise an undefined behaviour
 * could be observed
 */
@DangerousInternalIoApi
public inline fun Input.takeWhileSize(initialSize: Int = 1, block: (Buffer) -> Int) {
    var release = true
    var current = prepareReadFirstHead(initialSize) ?: return
    var size = initialSize

    try {
        do {
            val before = current.readRemaining
            val after: Int

            if (before >= size) {
                try {
                    size = block(current)
                } finally {
                    after = current.readRemaining
                }
            } else {
                after = before
            }

            release = false

            val next = when {
                after == 0 -> prepareReadNextHead(current)
                after < size || current.endGap < Buffer.ReservedSize -> {
                    completeReadHead(current)
                    prepareReadFirstHead(size)
                }
                else -> current
            }

            if (next == null) {
                break
            }

            current = next
            release = true
        } while (size > 0)
    } finally {
        if (release) {
            completeReadHead(current)
        }
    }
}

@ExperimentalIoApi
public fun Input.peekCharUtf8(): Char {
    val rc = tryPeek()
    if (rc and 0x80 == 0) return rc.toChar()
    if (rc == -1) throw EOFException("Failed to peek a char: end of input")

    return peekCharUtf8Impl(rc)
}

/**
 * For every byte from this input invokes [block] function giving it as parameter.
 */
@ExperimentalIoApi
public inline fun Input.forEach(block: (Byte) -> Unit) {
    takeWhile { buffer ->
        buffer.forEach(block)
        true
    }
}

private fun Input.peekCharUtf8Impl(first: Int): Char {
    var rc = '?'
    var found = false

    takeWhileSize(byteCountUtf8(first)) {
        it.decodeUTF8 { ch ->
            found = true
            rc = ch
            false
        }
    }

    if (!found) {
        throw MalformedUTF8InputException("No UTF-8 character found")
    }

    return rc
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
public fun Input.readAvailable(dst: ChunkBuffer, size: Int = dst.writeRemaining): Int = readAvailable(dst, size)

@JvmName("readAvailable")
@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
public fun Input.readAvailableOld(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    return readAvailable(dst, offset, length)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readAvailable")
public fun Input.readAvailableOld(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int =
    readAvailable(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readAvailable")
public fun Input.readAvailableOld(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int =
    readAvailable(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readAvailable")
public fun Input.readAvailableOld(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Int =
    readAvailable(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readAvailable")
public fun Input.readAvailableOld(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Int =
    readAvailable(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readAvailable")
public fun Input.readAvailableOld(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Int =
    readAvailable(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
public fun Input.readFully(dst: ChunkBuffer, size: Int = dst.writeRemaining): Unit = readFully(dst, size)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER", "UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("readFully")
public fun Input.readFullyOld(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Unit =
    readFully(dst, offset, length)
