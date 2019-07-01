@file:Suppress("ReplaceRangeToWithUntil", "RedundantModalityModifier", "DEPRECATION", "DEPRECATION_ERROR")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import org.khronos.webgl.*
import kotlin.contracts.*

@Suppress("DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES")
@Deprecated("Use Buffer instead.", replaceWith = ReplaceWith("Buffer", "kotlinx.io.core.Buffer"))
actual class IoBuffer actual constructor(
    memory: Memory,
    origin: ChunkBuffer?
) : Input, Output, ChunkBuffer(memory, origin) {
    private val content: ArrayBuffer get() = memory.view.buffer

    override val endOfInput: Boolean get() = writePosition == readPosition

    @Deprecated(
        "Not supported anymore. All operations are big endian by default. " +
            "Read/write with readXXXLittleEndian/writeXXXLittleEndian or " +
            "do readXXX/writeXXX with X.reverseByteOrder() instead.",
        level = DeprecationLevel.ERROR
    )
    actual final override var byteOrder: ByteOrder
        get() = ByteOrder.BIG_ENDIAN
        set(newOrder) {
            if (newOrder != ByteOrder.BIG_ENDIAN) {
                throw IllegalArgumentException("Only big endian is supported")
            }
        }

    final override fun peekTo(destination: Memory, destinationOffset: Long, offset: Long, min: Long, max: Long): Long {
        return (this as Buffer).peekTo(destination, destinationOffset, offset, min, max)
    }

    final override fun tryPeek(): Int {
        return tryPeekByte()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ArrayBuffer, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: ArrayBuffer, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: IoBuffer, length: Int): Int {
        return (this as Buffer).readAvailable(dst, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: ArrayBufferView, offset: Int, length: Int) {
        if (readRemaining < length) throw IllegalStateException("Not enough bytes available ($readRemaining) to read $length bytes")
        if (length > dst.byteLength) throw IllegalArgumentException("Destination buffer overflow: length = $length, buffer capacity ${dst.byteLength}")
        require(offset >= 0) { "offset should be positive" }
        require(offset + length <= dst.byteLength) { throw IndexOutOfBoundsException("") }

        (this as Buffer).readFully(dst.buffer, dst.byteOffset + offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: ArrayBufferView, offset: Int, length: Int): Int {
        val readRemaining = readRemaining
        if (readRemaining == 0) return -1
        val size = minOf(length, readRemaining)
        (this as Buffer).readFully(dst, offset, size)
        return size
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: Int8Array, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: Int8Array, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: IoBuffer, length: Int) {
        (this as Buffer).readFully(dst, length)
    }

    final override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        val idx = appendChars(csq ?: "null", start, end)
        if (idx != end) throw IllegalStateException("Not enough free space to append char sequence")
        return this
    }

    final override fun append(csq: CharSequence?): Appendable {
        return if (csq == null) append("null") else append(csq, 0, csq.length)
    }

    final override fun append(csq: CharArray, start: Int, end: Int): Appendable {
        val idx = appendChars(csq, start, end)

        if (idx != end) throw IllegalStateException("Not enough free space to append char sequence")
        return this
    }

    override fun append(c: Char): Appendable {
        (this as Buffer).append(c)
        return this
    }

    @Deprecated(
        "Use writeFully instead",
        ReplaceWith("writeFully(array, offset, length)"),
        level = DeprecationLevel.ERROR
    )
    fun write(array: ByteArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(array, offset, length)
    }

    @Deprecated(
        "Use writeFully instead",
        ReplaceWith("writeFully(array, offset, length)"),
        level = DeprecationLevel.ERROR
    )
    fun write(src: Int8Array, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readShort(): Short {
        return (this as Buffer).readShort()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readInt(): Int {
        return (this as Buffer).readInt()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFloat(): Float {
        return (this as Buffer).readFloat()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readDouble(): Double {
        return (this as Buffer).readDouble()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: ByteArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: ShortArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: IntArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: LongArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: FloatArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readFully(dst: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: IntArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: LongArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun peekTo(buffer: IoBuffer): Int {
        return (this as Input).peekTo(buffer)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readLong(): Long {
        return (this as Buffer).readLong()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeShort(v: Short) {
        (this as Buffer).writeShort(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeInt(v: Int) {
        (this as Buffer).writeInt(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFloat(v: Float) {
        (this as Buffer).writeFloat(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeDouble(v: Double) {
        (this as Buffer).writeDouble(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: ByteArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: ShortArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: IntArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: LongArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: FloatArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: IoBuffer, length: Int) {
        (this as Buffer).writeFully(src, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun fill(n: Long, v: Byte) {
        (this as Buffer).fill(n, v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeLong(v: Long) {
        (this as Buffer).writeLong(v)
    }

    @Deprecated("Use writeFully instead", ReplaceWith("writeFully(src, length)"), level = DeprecationLevel.ERROR)
    fun writeBuffer(src: IoBuffer, length: Int): Int {
        (this as Buffer).writeFully(src, length)
        return length
    }

    actual final override fun flush() {
    }

    @PublishedApi
    internal fun readableView(): DataView {
        val readPosition = readPosition
        val writePosition = writePosition

        return when {
            readPosition == writePosition -> EmptyDataView
            readPosition == 0 && writePosition == content.byteLength -> memory.view
            else -> DataView(content, readPosition, writePosition - readPosition)
        }
    }

    @PublishedApi
    internal fun writableView(): DataView {
        val writePosition = writePosition
        val limit = limit

        return when {
            writePosition == limit -> EmptyDataView
            writePosition == 0 && limit == content.byteLength -> memory.view
            else -> DataView(content, writePosition, limit - writePosition)
        }
    }

    /**
     * Apply [block] function on a [DataView] of readable bytes.
     * The [block] function should return number of consumed bytes.
     * @return number of bytes consumed
     */
    @ExperimentalIoApi
    inline fun readDirect(block: (DataView) -> Int): Int {
        val view = readableView()
        val rc = block(view)
        check(rc >= 0) { "The returned value from block function shouldn't be negative: $rc" }
        discard(rc)
        return rc
    }

    /**
     * Apply [block] function on a [DataView] of the free space.
     * The [block] function should return number of written bytes.
     * @return number of bytes written
     */
    @ExperimentalIoApi
    inline fun writeDirect(block: (DataView) -> Int): Int {
        val view = writableView()
        val rc = block(view)
        check(rc >= 0) { "The returned value from block function shouldn't be negative: $rc" }
        check(rc <= writeRemaining) { "The returned value from block function is too big: $rc > $writeRemaining" }
        commitWritten(rc)
        return rc
    }

    actual fun release(pool: ObjectPool<IoBuffer>) {
        releaseImpl(pool)
    }

    actual override fun close() {
        throw UnsupportedOperationException("close for buffer view is not supported")
    }

    override fun toString(): String =
        "Buffer[readable = $readRemaining, writable = $writeRemaining, startGap = $startGap, endGap = $endGap]"

    actual companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [IoBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        @DangerousInternalIoApi
        actual val ReservedSize: Int
            get() = Buffer.ReservedSize

        private val EmptyBuffer = ArrayBuffer(0)
        private val EmptyDataView = DataView(EmptyBuffer)

        actual val Empty = IoBuffer(Memory.Empty, null)

        /**
         * The default buffer pool
         */
        actual val Pool: ObjectPool<IoBuffer> = object : DefaultPool<IoBuffer>(BUFFER_VIEW_POOL_SIZE) {
            override fun produceInstance(): IoBuffer {
                return IoBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null)
            }

            override fun clearInstance(instance: IoBuffer): IoBuffer {
                return super.clearInstance(instance).apply {
                    unpark()
                    reset()
                }
            }

            override fun validateInstance(instance: IoBuffer) {
                super.validateInstance(instance)

                require(instance.referenceCount == 0) { "unable to recycle buffer: buffer view is in use (refCount = ${instance.referenceCount})" }
                require(instance.origin == null) { "Unable to recycle buffer view: view copy shouldn't be recycled" }
            }

            override fun disposeInstance(instance: IoBuffer) {
                DefaultAllocator.free(instance.memory)
                instance.unlink()
            }
        }

        actual val NoPool: ObjectPool<IoBuffer> = object : NoPoolImpl<IoBuffer>() {
            override fun borrow(): IoBuffer {
                return IoBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null)
            }

            override fun recycle(instance: IoBuffer) {
                DefaultAllocator.free(instance.memory)
            }
        }

        actual val EmptyPool: ObjectPool<IoBuffer> = EmptyBufferPoolImpl
    }
}

fun Buffer.readFully(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        memory.copyTo(dst, start, length, offset)
        length
    }
}

fun Buffer.readFully(dst: ArrayBufferView, offset: Int = 0, length: Int = dst.byteLength - offset) {
    read { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw EOFException("Not enough bytes available to read $length bytes")
        }

        memory.copyTo(dst, start, length, offset)
        length
    }
}

fun Buffer.readAvailable(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    if (!canRead()) return -1
    val readSize = minOf(length, readRemaining)
    readFully(dst, offset, readSize)
    return readSize
}

fun Buffer.readAvailable(dst: ArrayBufferView, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    if (!canRead()) return -1
    val readSize = minOf(length, readRemaining)
    readFully(dst, offset, readSize)
    return readSize
}

fun Buffer.writeFully(src: ArrayBuffer, offset: Int = 0, length: Int = src.byteLength) {
    write { memory, start, endExclusive ->
        if (endExclusive - start < length) {
            throw InsufficientSpaceException("Not enough free space to write $length bytes")
        }

        src.copyTo(memory, offset, length, start)
        length
    }
}

fun Buffer.writeFully(src: ArrayBufferView, offset: Int = 0, length: Int = src.byteLength - offset) {
    write { memory, dstOffset, endExclusive ->
        if (endExclusive - dstOffset < length) {
            throw InsufficientSpaceException("Not enough free space to write $length bytes")
        }

        src.copyTo(memory, offset, length, dstOffset)
        length
    }
}

inline fun Buffer.writeDirect(block: (DataView) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).view)
    }
}

inline fun Buffer.readDirect(block: (DataView) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).view)
    }
}


inline fun Buffer.writeDirectInt8Array(block: (Int8Array) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, endExclusive ->
        block(Int8Array(memory.view.buffer, memory.view.byteOffset + start, endExclusive - start))
    }
}

inline fun Buffer.readDirectInt8Array(block: (Int8Array) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, endExclusive ->
        block(Int8Array(memory.view.buffer, memory.view.byteOffset + start, endExclusive - start))
    }
}
