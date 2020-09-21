@file:Suppress("DEPRECATION_ERROR", "DEPRECATION", "RedundantModalityModifier")
package io.ktor.utils.io.core

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import platform.posix.*
import kotlin.contracts.*
import kotlin.native.concurrent.*

@PublishedApi
@SharedImmutable
internal val MAX_SIZE: size_t = size_t.MAX_VALUE

@Suppress("DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES")
@Deprecated("Use Buffer instead.", replaceWith = ReplaceWith("Buffer", "io.ktor.utils.io.core.Buffer"))
public actual class IoBuffer actual constructor(
    memory: Memory,
    origin: ChunkBuffer?
) : Input, Output, ChunkBuffer(memory, origin) {
    internal var refCount by shared(1)

    private val contentCapacity: Int get() = memory.size32

    public constructor(content: CPointer<ByteVar>, contentCapacity: Int) : this(Memory.of(content, contentCapacity), null)

    override val endOfInput: Boolean get() = !canRead()

    init {
        require(contentCapacity >= 0) { "contentCapacity shouln't be negative: $contentCapacity" }
        require(this !== origin) { "origin shouldn't point to itself" }
    }

    @Deprecated(
        "Not supported anymore. All operations are big endian by default.",
        level = DeprecationLevel.ERROR
    )
    actual final override var byteOrder: ByteOrder
        get() = ByteOrder.BIG_ENDIAN
        set(newOrder) {
            if (newOrder != ByteOrder.BIG_ENDIAN) {
                throw IllegalArgumentException("Only BIG_ENDIAN is supported")
            }
        }

    final override fun peekTo(destination: Memory, destinationOffset: Long, offset: Long, min: Long, max: Long): Long {
        return (this as Buffer).peekTo(destination, destinationOffset, offset, min, max)
    }

    final override fun tryPeek(): Int {
        return tryPeekByte()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readShort(): Short {
        return (this as Buffer).readShort()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeShort(v: Short) {
        (this as Buffer).writeShort(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readInt(): Int {
        return (this as Buffer).readInt()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeInt(v: Int) {
        (this as Buffer).writeInt(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFloat(): Float {
        return (this as Buffer).readFloat()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFloat(v: Float) {
        (this as Buffer).writeFloat(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readDouble(): Double {
        return (this as Buffer).readDouble()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeDouble(v: Double) {
        (this as Buffer).writeDouble(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        (this as Buffer).readFully(dst, offset, length.toIntOrFail("length"))
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: CPointer<ByteVar>, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: CPointer<ByteVar>, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
        (this as Buffer).writeFully(src, offset, length.toIntOrFail("length"))
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ByteArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ShortArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: IntArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: LongArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)

    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: FloatArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: IoBuffer, length: Int) {
        (this as Buffer).readFully(dst, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: IntArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: LongArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: IoBuffer, length: Int): Int {
        return (this as Buffer).readAvailable(dst, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: CPointer<ByteVar>, offset: Long, length: Long): Long {
        return (this as Buffer).readAvailable(dst, offset, length.toIntOrFail("length")).toLong()
    }

    /**
     * Apply [block] to a native pointer for writing to the buffer. Lambda should return number of bytes were written.
     * @return number of bytes written
     */
    public fun writeDirect(block: (CPointer<ByteVar>) -> Int): Int {
        val rc = block((content + writePosition)!!)
        check(rc >= 0) { "block function should return non-negative results: $rc" }
        check(rc <= writeRemaining)
        commitWritten(rc)
        return rc
    }

    /**
     * Apply [block] to a native pointer for reading from the buffer. Lambda should return number of bytes were read.
     * @return number of bytes read
     */
    public fun readDirect(block: (CPointer<ByteVar>) -> Int): Int {
        val rc = block((content + readPosition)!!)
        check(rc >= 0) { "block function should return non-negative results: $rc" }
        check(rc <= readRemaining) { "result value is too large: $rc > $readRemaining" }
        discard(rc)
        return rc
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: ByteArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: ShortArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: IntArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: LongArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: FloatArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: IoBuffer, length: Int) {
        (this as Buffer).writeFully(src, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun fill(n: Long, v: Byte) {
        (this as Buffer).fill(n, v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun readLong(): Long {
        return (this as Buffer).readLong()
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeLong(v: Long) {
        (this as Buffer).writeLong(v)
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

    public fun appendChars(csq: CharArray, start: Int, end: Int): Int {
        return (this as Buffer).appendChars(csq, start, end)
    }

    public fun appendChars(csq: CharSequence, start: Int, end: Int): Int {
        return (this as Buffer).appendChars(csq, start, end)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun peekTo(buffer: IoBuffer): Int {
        return (this as Input).peekTo(buffer)
    }

    public fun makeView(): IoBuffer {
        return duplicate()
    }

    override fun duplicate(): IoBuffer = (origin ?: this).let { newOrigin ->
        newOrigin.acquire()
        IoBuffer(memory, newOrigin).also { copy ->
            duplicateTo(copy)
        }
    }

    actual final override fun flush() {
    }

    actual override fun close() {
        throw UnsupportedOperationException("close for buffer view is not supported")
    }

    public actual fun release(pool: ObjectPool<IoBuffer>) {
        releaseImpl(pool)
    }

    override fun toString(): String =
        "Buffer[readable = $readRemaining, writable = $writeRemaining, startGap = $startGap, endGap = $endGap]"

    public actual companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [IoBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        @DangerousInternalIoApi
        public actual val ReservedSize: Int get() = Buffer.ReservedSize

        internal val EmptyBuffer = nativeHeap.allocArray<ByteVar>(0)

        public actual val Empty: IoBuffer = IoBuffer(Memory.Empty, null)

        /**
         * The default buffer pool
         */
        public actual val Pool: ObjectPool<IoBuffer> get() = BufferPoolNativeWorkaround

        public actual val NoPool: ObjectPool<IoBuffer> = object : NoPoolImpl<IoBuffer>() {
            override fun borrow(): IoBuffer {
                return IoBuffer(DefaultAllocator.alloc(DEFAULT_BUFFER_SIZE), null)
            }

            override fun recycle(instance: IoBuffer) {
                require(instance.refCount == 0) { "Couldn't dispose buffer: it is still in-use: refCount = ${instance.refCount}" }
                require(instance.content !== EmptyBuffer) { "Couldn't dispose empty buffer" }
                nativeHeap.free(instance.content)
            }
        }

        internal val NoPoolForManaged: ObjectPool<IoBuffer> = object : NoPoolImpl<IoBuffer>() {
            override fun borrow(): IoBuffer {
                error("You can't borrow an instance from this pool: use it only for manually created")
            }

            override fun recycle(instance: IoBuffer) {
                require(instance.refCount == 0) { "Couldn't dispose buffer: it is still in-use: refCount = ${instance.refCount}" }
                require(instance.content !== EmptyBuffer) { "Couldn't dispose empty buffer" }
            }
        }

        public actual val EmptyPool: ObjectPool<IoBuffer> = EmptyBufferPoolImpl
    }
}

@ThreadLocal
private object BufferPoolNativeWorkaround : DefaultPool<IoBuffer>(BUFFER_VIEW_POOL_SIZE) {
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
        require(instance.referenceCount == 0) { "Couldn't dispose buffer: it is still in-use: refCount = ${instance.referenceCount}" }
        nativeHeap.free(instance.memory)
    }
}

public fun Buffer.readFully(pointer: CPointer<ByteVar>, offset: Int, length: Int) {
    readFully(pointer, offset.toLong(), length)
}

public fun Buffer.readFully(pointer: CPointer<ByteVar>, offset: Long, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")
    readExact(length, "content") { memory, start ->
        memory.copyTo(pointer, start.toLong(), length.toLong(), offset)
    }
}

public fun Buffer.readAvailable(pointer: CPointer<ByteVar>, offset: Int, length: Int): Int {
    return readAvailable(pointer, offset.toLong(), length)
}

public fun Buffer.readAvailable(pointer: CPointer<ByteVar>, offset: Long, length: Int): Int {
    val available = readRemaining
    if (available == 0) return -1
    val resultSize = minOf(available, length)
    readFully(pointer, offset, resultSize)
    return resultSize
}

public fun Buffer.writeFully(pointer: CPointer<ByteVar>, offset: Int, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")

    writeExact(length, "content") { memory, start ->
        pointer.copyTo(memory, offset, length, start)
    }
}

public fun Buffer.writeFully(pointer: CPointer<ByteVar>, offset: Long, length: Int) {
    requirePositiveIndex(offset, "offset")
    requirePositiveIndex(length, "length")

    writeExact(length, "content") { memory, start ->
        pointer.copyTo(memory, offset, length.toLong(), start.toLong())
    }
}

public inline fun Buffer.readDirect(block: (CPointer<ByteVar>) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, _ ->
        block(memory.pointer.plus(start)!!)
    }
}

public inline fun Buffer.writeDirect(block: (CPointer<ByteVar>) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, _ ->
        block(memory.pointer.plus(start)!!)
    }
}

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Int, origin: ChunkBuffer?): ChunkBuffer {
    return IoBuffer(Memory.of(ptr, lengthInBytes), origin)
}

public fun ChunkBuffer(ptr: CPointer<*>, lengthInBytes: Long, origin: ChunkBuffer?): ChunkBuffer {
    return IoBuffer(Memory.of(ptr, lengthInBytes), origin)
}
