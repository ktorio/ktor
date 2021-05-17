package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.locks.*
import kotlin.math.*

@Deprecated("This is going to become internal. Use ByteReadChannel receiver instead.", level = DeprecationLevel.ERROR)
public suspend fun ByteChannelSequentialBase.joinTo(dst: ByteChannelSequentialBase, closeOnEnd: Boolean) {
    return joinToImpl(dst, closeOnEnd)
}

@Deprecated("This is going to become internal. Use ByteReadChannel receiver instead.", level = DeprecationLevel.ERROR)
public suspend fun ByteChannelSequentialBase.copyTo(
    dst: ByteChannelSequentialBase,
    limit: Long = Long.MAX_VALUE
): Long {
    return copyToSequentialImpl(dst, limit)
}

private const val EXPECTED_CAPACITY: Long = 4088L

/**
 * Sequential (non-concurrent) byte channel implementation
 */
@Suppress("OverridingDeprecatedMember")
@DangerousInternalIoApi
public abstract class ByteChannelSequentialBase(
    initial: IoBuffer,
    override val autoFlush: Boolean,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : ByteChannel, ByteReadChannel, ByteWriteChannel, SuspendableReadSession, HasReadSession, HasWriteSession {

    @Suppress("unused", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public constructor(initial: IoBuffer, autoFlush: Boolean) : this(initial, autoFlush, ChunkBuffer.Pool)

    private val state = ByteChannelSequentialBaseSharedState()

    protected var closed: Boolean
        get() = state.closed
        set(value) {
            state.closed = value
        }

    protected val writable: BytePacketBuilder = BytePacketBuilder(0, pool)
    protected val readable: ByteReadPacket = ByteReadPacket(initial, pool)

    private val slot = AwaitingSlot()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun totalPending(): Int = availableForRead + writable.size

    private val flushSize: Int get() = flushBuffer.size

    override val availableForRead: Int
        get() = flushSize + readable.remaining.toInt()

    override val availableForWrite: Int
        get() = maxOf(0, EXPECTED_CAPACITY.toInt() - totalPending())

    @Deprecated(
        "Setting byte order is no longer supported. Read/write in big endian and use reverseByteOrder() extensions.",
        level = DeprecationLevel.ERROR
    )
    override var readByteOrder: ByteOrder
        get() = state.readByteOrder
        set(value) {
            state.readByteOrder = value
        }

    @Deprecated(
        "Setting byte order is no longer supported. Read/write in big endian and use reverseByteOrder() extensions.",
        level = DeprecationLevel.ERROR
    )
    override var writeByteOrder: ByteOrder
        get() = state.writeByteOrder
        set(value) {
            state.writeByteOrder = value
        }

    override val isClosedForRead: Boolean
        get() = closed && readable.isEmpty && flushSize == 0 && writable.isEmpty

    override val isClosedForWrite: Boolean
        get() = closed

    private var _totalBytesRead: Long
        get() = state.totalBytesRead
        set(value) {
            state.totalBytesRead = value
        }

    override val totalBytesRead: Long get() = state.totalBytesRead

    private var _totalBytesWritten: Long
        get() = state.totalBytesWritten
        set(value) {
            state.totalBytesWritten = value
        }

    override val totalBytesWritten: Long get() = state.totalBytesWritten

    final override var closedCause: Throwable?
        get() = state.closedCause
        private set(value) {
            state.closedCause = value
        }

    private val flushMutex = SynchronizedObject()
    private val flushBuffer: BytePacketBuilder = BytePacketBuilder()

    internal suspend fun awaitAtLeastNBytesAvailableForWrite(count: Int) {
        while (availableForWrite < count && !closed) {
            if (!flushImpl()) {
                slot.sleep()
            }
        }
    }

    internal suspend fun awaitAtLeastNBytesAvailableForRead(count: Int) {
        while (availableForRead < count && !closed) {
            slot.sleep()
        }
    }

    override fun flush() {
        flushImpl()
    }

    private fun flushImpl(): Boolean {
        if (writable.isEmpty) {
            return false
        }

        flushWrittenBytes()
        slot.resume()
        return true
    }

    /**
     * Send bytes to thread-safe storage.
     *
     * This method is writer-only safe.
     */
    private fun flushWrittenBytes() {
        synchronized(flushMutex) {
            val buffer = writable.stealAll()!!
            flushBuffer.writeChunkBuffer(buffer)
        }
    }

    /**
     * Take flushed bytes before read.
     *
     * This method is reader-only safe.
     */
    protected fun prepareFlushedBytes() {
        synchronized(flushMutex) {
            readable.unsafeAppend(flushBuffer)
        }
    }

    private fun ensureNotClosed() {
        if (closed) {
            throw closedCause ?: ClosedWriteChannelException("Channel is already closed")
        }
    }

    private fun ensureNotFailed() {
        closedCause?.let { throw it }
    }

    private fun ensureNotFailed(closeable: BytePacketBuilder) {
        closedCause?.let { cause ->
            closeable.release()
            throw cause
        }
    }

    override suspend fun writeByte(b: Byte) {
        awaitAtLeastNBytesAvailableForWrite(1)
        writable.writeByte(b)
        afterWrite(1)
    }

    private inline fun <T : Any> reverseWrite(value: () -> T, reversed: () -> T): T {
        @Suppress("DEPRECATION_ERROR")
        return if (writeByteOrder == ByteOrder.BIG_ENDIAN) {
            value()
        } else {
            reversed()
        }
    }

    override suspend fun writeShort(s: Short) {
        awaitAtLeastNBytesAvailableForWrite(2)
        writable.writeShort(reverseWrite({ s }, { s.reverseByteOrder() }))
        afterWrite(2)
    }

    override suspend fun writeInt(i: Int) {
        awaitAtLeastNBytesAvailableForWrite(4)
        writable.writeInt(reverseWrite({ i }, { i.reverseByteOrder() }))
        afterWrite(4)
    }

    override suspend fun writeLong(l: Long) {
        awaitAtLeastNBytesAvailableForWrite(8)
        writable.writeLong(reverseWrite({ l }, { l.reverseByteOrder() }))
        afterWrite(8)
    }

    override suspend fun writeFloat(f: Float) {
        awaitAtLeastNBytesAvailableForWrite(4)
        writable.writeFloat(reverseWrite({ f }, { f.reverseByteOrder() }))
        afterWrite(4)
    }

    override suspend fun writeDouble(d: Double) {
        awaitAtLeastNBytesAvailableForWrite(8)
        writable.writeDouble(reverseWrite({ d }, { d.reverseByteOrder() }))
        afterWrite(8)
    }

    override suspend fun writePacket(packet: ByteReadPacket) {
        awaitAtLeastNBytesAvailableForWrite(1)
        val size = packet.remaining.toInt()
        writable.writePacket(packet)
        afterWrite(size)
    }

    override suspend fun writeFully(src: IoBuffer) {
        writeFully(src as Buffer)
    }

    override suspend fun writeFully(src: Buffer) {
        awaitAtLeastNBytesAvailableForWrite(1)
        val count = src.readRemaining
        writable.writeFully(src)
        afterWrite(count)
    }

    override suspend fun writeFully(src: ByteArray, offset: Int, length: Int) {
        var currentIndex = offset
        val endIndex = offset + length

        while (currentIndex < endIndex) {
            awaitAtLeastNBytesAvailableForWrite(1)

            val bytesCount = min(availableForWrite, endIndex - currentIndex)
            writable.writeFully(src, currentIndex, bytesCount)

            currentIndex += bytesCount
            afterWrite(bytesCount)
        }
    }

    override suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int) {
        var currentIndex = startIndex

        while (currentIndex < endIndex) {
            awaitAtLeastNBytesAvailableForWrite(1)

            val bytesCount = min(availableForWrite, endIndex - currentIndex)
            writable.writeFully(memory, currentIndex, bytesCount)

            currentIndex += bytesCount
            afterWrite(bytesCount)
        }
    }

    override suspend fun writeAvailable(src: IoBuffer): Int {
        val srcRemaining = src.readRemaining
        if (srcRemaining == 0) return 0
        val size = minOf(srcRemaining, availableForWrite)

        return if (size == 0) writeAvailableSuspend(src)
        else {
            writable.writeFully(src, size)
            afterWrite(size)
            size
        }
    }

    override suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val size = minOf(length, availableForWrite)

        return if (size == 0) writeAvailableSuspend(src, offset, length)
        else {
            writable.writeFully(src, offset, size)
            afterWrite(size)
            size
        }
    }

    @ExperimentalIoApi
    @Suppress("DEPRECATION")
    override suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit) {
        val session = beginWriteSession()
        visitor(session)
    }

    @Suppress("DEPRECATION")
    override fun beginWriteSession(): WriterSuspendSession {
        return object : WriterSuspendSession {
            override fun request(min: Int): IoBuffer? {
                if (availableForWrite == 0) return null
                return writable.prepareWriteHead(min) as IoBuffer
            }

            override fun written(n: Int) {
                writable.afterHeadWrite()
                afterWrite(n)
            }

            override fun flush() {
                this@ByteChannelSequentialBase.flush()
            }

            override suspend fun tryAwait(n: Int) {
                if (availableForWrite < n) {
                    awaitAtLeastNBytesAvailableForWrite(n)
                }
            }
        }
    }

    override fun endWriteSession(written: Int) {
        writable.afterHeadWrite()
        afterWrite(written)
    }

    override suspend fun readByte(): Byte {
        return if (readable.isNotEmpty) {
            readable.readByte().also { afterRead(1) }
        } else {
            readByteSlow()
        }
    }

    private fun checkClosed(remaining: Int, closeable: BytePacketBuilder? = null) {
        closedCause?.let {
            closeable?.close()
            throw it
        }
        if (closed && availableForRead < remaining) {
            closeable?.close()
            throw EOFException("$remaining bytes required but EOF reached")
        }
    }

    private suspend fun readByteSlow(): Byte {
        do {
            awaitSuspend(1)

            if (readable.isNotEmpty) return readable.readByte().also { afterRead(1) }
            checkClosed(1)
        } while (true)
    }

    override suspend fun readShort(): Short {
        return if (readable.hasBytes(2)) {
            readable.readShort().reverseRead().also { afterRead(2) }
        } else {
            readShortSlow()
        }
    }

    private suspend fun readShortSlow(): Short {
        readNSlow(2) { return readable.readShort().reverseRead().also { afterRead(2) } }
    }

    @Deprecated("Consider providing consumed count of bytes", level = DeprecationLevel.ERROR)
    protected fun afterRead() {
        afterRead(0)
    }

    protected fun afterRead(count: Int) {
        _totalBytesRead += count
        slot.resume()
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Short.reverseRead(): Short = when {
        readByteOrder == ByteOrder.BIG_ENDIAN -> this
        else -> this.reverseByteOrder()
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Int.reverseRead(): Int = when {
        readByteOrder == ByteOrder.BIG_ENDIAN -> this
        else -> this.reverseByteOrder()
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Long.reverseRead(): Long = when {
        readByteOrder == ByteOrder.BIG_ENDIAN -> this
        else -> this.reverseByteOrder()
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Float.reverseRead(): Float = when {
        readByteOrder == ByteOrder.BIG_ENDIAN -> this
        else -> this.reverseByteOrder()
    }

    @Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")
    private inline fun Double.reverseRead(): Double = when {
        readByteOrder == ByteOrder.BIG_ENDIAN -> this
        else -> this.reverseByteOrder()
    }

    override suspend fun readInt(): Int {
        return if (readable.hasBytes(4)) {
            readable.readInt().reverseRead().also { afterRead(4) }
        } else {
            readIntSlow()
        }
    }

    private suspend fun readIntSlow(): Int {
        readNSlow(4) {
            return readable.readInt().reverseRead().also { afterRead(4) }
        }
    }

    override suspend fun readLong(): Long {
        return if (readable.hasBytes(8)) {
            readable.readLong().reverseRead().also { afterRead(8) }
        } else {
            readLongSlow()
        }
    }

    private suspend fun readLongSlow(): Long {
        readNSlow(8) {
            return readable.readLong().reverseRead().also { afterRead(8) }
        }
    }

    override suspend fun readFloat(): Float = if (readable.hasBytes(4)) {
        readable.readFloat().reverseRead().also { afterRead(4) }
    } else {
        readFloatSlow()
    }

    private suspend fun readFloatSlow(): Float {
        readNSlow(4) {
            return readable.readFloat().reverseRead().also { afterRead(4) }
        }
    }

    override suspend fun readDouble(): Double = if (readable.hasBytes(8)) {
        readable.readDouble().reverseRead().also { afterRead(8) }
    } else {
        readDoubleSlow()
    }

    private suspend fun readDoubleSlow(): Double {
        readNSlow(8) {
            return readable.readDouble().reverseRead().also { afterRead(8) }
        }
    }

    override suspend fun readRemaining(limit: Long, headerSizeHint: Int): ByteReadPacket {
        ensureNotFailed()

        val builder = BytePacketBuilder(headerSizeHint)

        val size = minOf(limit, readable.remaining)
        builder.writePacket(readable, size)
        val remaining = limit - builder.size

        return if (remaining == 0L || isClosedForRead) {
            afterRead(remaining.toInt())
            ensureNotFailed(builder)
            builder.build()
        } else {
            readRemainingSuspend(builder, limit)
        }
    }

    private suspend fun readRemainingSuspend(builder: BytePacketBuilder, limit: Long): ByteReadPacket {
        while (builder.size < limit) {
            val partLimit = minOf(limit - builder.size, readable.remaining)
            builder.writePacket(readable, partLimit)
            afterRead(partLimit.toInt())
            ensureNotFailed(builder)

            if (isClosedForRead || builder.size == limit.toInt()) {
                break
            }

            awaitSuspend(1)
        }

        ensureNotFailed(builder)
        return builder.build()
    }

    override suspend fun readPacket(size: Int, headerSizeHint: Int): ByteReadPacket {
        checkClosed(size)

        val builder = BytePacketBuilder(headerSizeHint)

        var remaining = size
        val partSize = minOf(remaining.toLong(), readable.remaining).toInt()
        remaining -= partSize
        builder.writePacket(readable, partSize)
        afterRead(partSize)
        checkClosed(remaining, builder)

        return if (remaining > 0) readPacketSuspend(builder, remaining)
        else builder.build()
    }

    private suspend fun readPacketSuspend(builder: BytePacketBuilder, size: Int): ByteReadPacket {
        var remaining = size
        while (remaining > 0) {
            val partSize = minOf(remaining.toLong(), readable.remaining).toInt()
            remaining -= partSize
            builder.writePacket(readable, partSize)
            afterRead(partSize)
            checkClosed(remaining, builder)

            if (remaining > 0) {
                awaitSuspend(1)
            }
        }

        checkClosed(remaining, builder)
        return builder.build()
    }

    protected fun readAvailableClosed(): Int {
        closedCause?.let { throw it }

        if (availableForRead > 0) {
            prepareFlushedBytes()
        }

        return -1
    }

    override suspend fun readAvailable(dst: IoBuffer): Int = readAvailable(dst as Buffer)

    internal suspend fun readAvailable(dst: Buffer): Int {
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (dst.writeRemaining == 0) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val size = minOf(dst.writeRemaining.toLong(), readable.remaining).toInt()
        readable.readFully(dst, size)
        afterRead(size)
        return size
    }

    override suspend fun readFully(dst: IoBuffer, n: Int) {
        readFully(dst as Buffer, n)
    }

    private suspend fun readFully(dst: Buffer, n: Int) {
        require(n <= dst.writeRemaining) { "Not enough space in the destination buffer to write $n bytes" }
        require(n >= 0) { "n shouldn't be negative" }

        return when {
            closedCause != null -> throw closedCause!!
            readable.remaining >= n -> readable.readFully(dst, n).also { afterRead(n) }
            closed -> throw EOFException(
                "Channel is closed and not enough bytes available: required $n but $availableForRead available"
            )
            else -> readFullySuspend(dst, n)
        }
    }

    private suspend fun readFullySuspend(dst: Buffer, n: Int) {
        awaitSuspend(n)
        return readFully(dst, n)
    }

    override suspend fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (length == 0) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val size = minOf(length.toLong(), readable.remaining).toInt()
        readable.readFully(dst, offset, size)
        afterRead(size)
        return size
    }

    override suspend fun readFully(dst: ByteArray, offset: Int, length: Int) {
        val rc = readAvailable(dst, offset, length)
        if (rc == length) return
        if (rc == -1) throw EOFException("Unexpected end of stream")

        return readFullySuspend(dst, offset + rc, length - rc)
    }

    private suspend fun readFullySuspend(dst: ByteArray, offset: Int, length: Int) {
        var written = 0

        while (written < length) {
            val rc = readAvailable(dst, offset + written, length - written)
            if (rc == -1) throw EOFException("Unexpected end of stream")
            written += rc
        }
    }

    override suspend fun readBoolean(): Boolean {
        return if (readable.canRead()) (readable.readByte() == 1.toByte()).also { afterRead(1) }
        else readBooleanSlow()
    }

    private suspend fun readBooleanSlow(): Boolean {
        awaitSuspend(1)
        checkClosed(1)
        return readBoolean()
    }

    private var lastReadAvailable: Int
        get() = state.lastReadAvailable
        set(value) {
            state.lastReadAvailable = value
        }

    private var lastReadView: ChunkBuffer
        get() = state.lastReadView
        set(value) {
            state.lastReadView = value
        }

    private fun completeReading() {
        val remaining = lastReadView.readRemaining
        val delta = lastReadAvailable - remaining
        if (lastReadView !== Buffer.Empty) {
            readable.completeReadHead(lastReadView)
        }
        if (delta > 0) {
            afterRead(delta)
        }
        lastReadAvailable = 0
        lastReadView = ChunkBuffer.Empty
    }

    override suspend fun await(atLeast: Int): Boolean {
        require(atLeast >= 0) { "atLeast parameter shouldn't be negative: $atLeast" }
        require(atLeast <= EXPECTED_CAPACITY) {
            "atLeast parameter shouldn't be larger than max buffer size of $EXPECTED_CAPACITY: $atLeast"
        }

        completeReading()

        if (atLeast == 0) return !isClosedForRead
        if (readable.remaining >= atLeast) return true

        return awaitSuspend(atLeast)
    }

    internal suspend fun awaitInternalAtLeast1(): Boolean = if (readable.isNotEmpty) {
        true
    } else {
        awaitSuspend(1)
    }

    protected suspend fun awaitSuspend(atLeast: Int): Boolean {
        require(atLeast >= 0)

        awaitAtLeastNBytesAvailableForRead(atLeast)
        prepareFlushedBytes()

        closedCause?.let { throw it }
        return !isClosedForRead && availableForRead >= atLeast
    }

    override fun discard(n: Int): Int {
        closedCause?.let { throw it }

        if (n == 0) {
            return 0
        }

        return readable.discard(n).also {
            afterRead(n)
            requestNextView(1)
        }
    }

    override fun request(atLeast: Int): IoBuffer? {
        closedCause?.let { throw it }

        completeReading()

        return requestNextView(atLeast)
    }

    private fun requestNextView(atLeast: Int): IoBuffer? {
        if (readable.isEmpty) {
            prepareFlushedBytes()
        }

        val view = readable.prepareReadHead(atLeast) as IoBuffer?

        if (view == null) {
            lastReadView = ChunkBuffer.Empty
            lastReadAvailable = 0
        } else {
            lastReadView = view
            lastReadAvailable = view.readRemaining
        }

        return view
    }

    override suspend fun discard(max: Long): Long {
        val discarded = readable.discard(max)

        return if (discarded == max || isClosedForRead) {
            ensureNotFailed()
            return discarded
        } else {
            discardSuspend(max, discarded)
        }
    }

    private suspend fun discardSuspend(max: Long, discarded0: Long): Long {
        var discarded = discarded0

        do {
            if (!await(1)) break
            discarded += readable.discard(max - discarded)
        } while (discarded < max && !isClosedForRead)

        ensureNotFailed()

        return discarded
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use read instead.")
    override fun readSession(consumer: ReadSession.() -> Unit) {
        try {
            consumer(this)
        } finally {
            completeReading()
        }
    }

    override fun startReadSession(): SuspendableReadSession = this

    override fun endReadSession() {
        completeReading()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use read instead.")
    override suspend fun readSuspendableSession(consumer: suspend SuspendableReadSession.() -> Unit) {
        try {
            consumer(this)
        } finally {
            completeReading()
        }
    }

    override suspend fun <A : Appendable> readUTF8LineTo(out: A, limit: Int): Boolean {
        if (isClosedForRead) {
            val cause = closedCause
            if (cause != null) {
                throw cause
            }

            return false
        }

        @OptIn(DangerousInternalIoApi::class)
        return decodeUTF8LineLoopSuspend(out, limit) { size ->
            afterRead(size)
            if (await(size)) readable
            else null
        }
    }

    override suspend fun readUTF8Line(limit: Int): String? {
        val sb = StringBuilder()
        if (!readUTF8LineTo(sb, limit)) {
            return null
        }

        return sb.toString()
    }

    override fun cancel(cause: Throwable?): Boolean {
        if (closedCause != null || closed) {
            return false
        }

        return close(cause ?: io.ktor.utils.io.CancellationException("Channel cancelled"))
    }

    override fun close(cause: Throwable?): Boolean {
        if (closed || closedCause != null) return false
        closedCause = cause
        closed = true
        if (cause != null) {
            readable.release()
            writable.release()
            flushBuffer.release()
        } else {
            flush()
        }

        slot.cancel(cause)
        return true
    }

    internal fun transferTo(dst: ByteChannelSequentialBase, limit: Long): Long {
        val size = readable.remaining
        return if (size <= limit) {
            dst.writable.writePacket(readable)
            dst.afterWrite(size.toInt())
            afterRead(size.toInt())
            size
        } else {
            0
        }
    }

    private suspend inline fun readNSlow(n: Int, block: () -> Nothing): Nothing {
        do {
            awaitSuspend(n)

            if (readable.hasBytes(n)) block()
            checkClosed(n)
        } while (true)
    }

    @Suppress("DEPRECATION")
    private suspend fun writeAvailableSuspend(src: IoBuffer): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src)
    }

    private suspend fun writeAvailableSuspend(src: ByteArray, offset: Int, length: Int): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src, offset, length)
    }

    @Deprecated("Consider providing written count of bytes", level = DeprecationLevel.ERROR)
    protected fun afterWrite() {
        afterWrite(0)
    }

    protected fun afterWrite(count: Int) {
        _totalBytesWritten += count

        if (closed) {
            writable.release()
            ensureNotClosed()
        }
        if (autoFlush || availableForWrite == 0) {
            flush()
        }
    }

    override suspend fun awaitFreeSpace() {
        flush()
        awaitAtLeastNBytesAvailableForWrite(1)
        ensureNotClosed()
    }

    final override suspend fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long,
        min: Long,
        max: Long
    ): Long {
        var bytesCopied = 0L

        @Suppress("DEPRECATION")
        readSuspendableSession {
            val desiredSize = (min + offset).coerceAtMost(EXPECTED_CAPACITY).toInt()

            await(desiredSize)

            val buffer = request(1) ?: IoBuffer.Empty
            if (buffer.readRemaining > offset) {
                bytesCopied = minOf(buffer.readRemaining.toLong() - offset, max, destination.size - destinationOffset)
                buffer.memory.copyTo(destination, offset, bytesCopied, destinationOffset)
            }
        }

        return bytesCopied
    }
}
