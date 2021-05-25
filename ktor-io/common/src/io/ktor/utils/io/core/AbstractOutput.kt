@file:Suppress("LocalVariableName", "RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * The default [Output] implementation.
 * @see flush
 * @see closeDestination
 */
@ExperimentalIoApi
@Deprecated(
    "AbstractOutput is deprecated and will be merged with Input in 2.0.0",
    ReplaceWith("Output"),
    DeprecationLevel.WARNING
)
public abstract class AbstractOutput
internal constructor(
    private val headerSizeHint: Int,
    protected val pool: ObjectPool<ChunkBuffer>
) : Appendable, Output {
    public constructor(pool: ObjectPool<ChunkBuffer>) : this(0, pool)

    public constructor() : this(ChunkBuffer.Pool)

    /**
     * An implementation should write [source] to the destination exactly [length] bytes.
     * It should never capture the [source] instance
     * longer than this method execution since it may be disposed after return.
     */
    protected abstract fun flush(source: Memory, offset: Int, length: Int)

    /**
     * An implementation should only close the destination.
     */
    protected abstract fun closeDestination()

    private val state = AbstractOutputSharedState()

    private var _head: ChunkBuffer?
        get() = state.head
        set(value) {
            state.head = value
        }

    private var _tail: ChunkBuffer?
        get() = state.tail
        set(value) {
            state.tail = value
        }

    internal val head: ChunkBuffer
        get() = _head ?: ChunkBuffer.Empty

    @PublishedApi
    @Deprecated("Will be removed in future releases.", level = DeprecationLevel.HIDDEN)
    internal val tail: ChunkBuffer
        get() {
            return prepareWriteHead(1)
        }

    @Deprecated("Will be removed. Override flush(buffer) properly.", level = DeprecationLevel.ERROR)
    protected var currentTail: ChunkBuffer
        get() = prepareWriteHead(1)
        set(newValue) {
            appendChain(newValue)
        }

    internal var tailMemory: Memory
        get() = state.tailMemory
        set(value) {
            state.tailMemory = value
        }

    internal var tailPosition
        get() = state.tailPosition
        set(value) {
            state.tailPosition = value
        }

    internal var tailEndExclusive
        get() = state.tailEndExclusive
        private set(value) {
            state.tailEndExclusive = value
        }

    private var tailInitialPosition
        get() = state.tailInitialPosition
        set(value) {
            state.tailInitialPosition = value
        }

    /**
     * Number of bytes buffered in the chain except the tail chunk
     */
    private var chainedSize: Int
        get() = state.chainedSize
        set(value) {
            state.chainedSize = value
        }

    internal inline val tailRemaining: Int get() = tailEndExclusive - tailPosition

    /**
     * Number of bytes currently buffered (pending).
     */
    protected final var _size: Int
        get() = chainedSize + (tailPosition - tailInitialPosition)
        @Deprecated("There is no need to update/reset this value anymore.")
        set(_) {
        }

    /**
     * Byte order (Endianness) to be used by future write functions calls on this builder instance. Doesn't affect any
     * previously written values.
     * @default [ByteOrder.BIG_ENDIAN]
     */
    @Deprecated(
        "This is no longer supported. All operations are big endian by default. Use readXXXLittleEndian " +
            "to read primitives in little endian",
        level = DeprecationLevel.ERROR
    )
    final override var byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
        set(value) {
            field = value
            if (value != ByteOrder.BIG_ENDIAN) {
                throw IllegalArgumentException(
                    "Only BIG_ENDIAN is supported. Use corresponding functions to read/write" +
                        "in the little endian"
                )
            }
        }

    final override fun flush() {
        flushChain()
    }

    private fun flushChain() {
        val oldTail = stealAll() ?: return

        try {
            oldTail.forEachChunk { chunk ->
                flush(chunk.memory, chunk.readPosition, chunk.readRemaining)
            }
        } finally {
            oldTail.releaseAll(pool)
        }
    }

    /**
     * Detach all chunks and cleanup all internal state so builder could be reusable again
     * @return a chain of buffer views or `null` of it is empty
     */
    internal fun stealAll(): ChunkBuffer? {
        val head = this._head ?: return null

        _tail?.commitWrittenUntilIndex(tailPosition)

        this._head = null
        this._tail = null
        tailPosition = 0
        tailEndExclusive = 0
        tailInitialPosition = 0
        chainedSize = 0
        tailMemory = Memory.Empty

        return head
    }

    internal fun afterBytesStolen() {
        val head = head
        if (head !== ChunkBuffer.Empty) {
            check(head.next == null)
            head.resetForWrite()
            head.reserveStartGap(headerSizeHint)
            head.reserveEndGap(Buffer.ReservedSize)
            tailPosition = head.writePosition
            tailInitialPosition = tailPosition
            tailEndExclusive = head.limit
        }
    }

    internal final fun appendSingleChunk(buffer: ChunkBuffer) {
        check(buffer.next == null) { "It should be a single buffer chunk." }
        appendChainImpl(buffer, buffer, 0)
    }

    internal final fun appendChain(head: ChunkBuffer) {
        val tail = head.findTail()
        val chainedSizeDelta = (head.remainingAll() - tail.readRemaining).toIntOrFail("total size increase")
        appendChainImpl(head, tail, chainedSizeDelta)
    }

    private fun appendNewChunk(): ChunkBuffer {
        val new = pool.borrow()
        new.reserveEndGap(Buffer.ReservedSize)

        appendSingleChunk(new)

        return new
    }

    private final fun appendChainImpl(head: ChunkBuffer, newTail: ChunkBuffer, chainedSizeDelta: Int) {
        val _tail = _tail
        if (_tail == null) {
            _head = head
            chainedSize = 0
        } else {
            _tail.next = head
            val tailPosition = tailPosition
            _tail.commitWrittenUntilIndex(tailPosition)
            chainedSize += tailPosition - tailInitialPosition
        }

        this._tail = newTail
        chainedSize += chainedSizeDelta
        tailMemory = newTail.memory
        tailPosition = newTail.writePosition
        tailInitialPosition = newTail.readPosition
        tailEndExclusive = newTail.limit
    }

    final override fun writeByte(v: Byte) {
        val index = tailPosition
        if (index < tailEndExclusive) {
            tailPosition = index + 1
            tailMemory[index] = v
            return
        }

        return writeByteFallback(v)
    }

    private fun writeByteFallback(v: Byte) {
        appendNewChunk().writeByte(v)
        tailPosition++
    }

    /**
     * Should flush and close the destination
     */
    final override fun close() {
        try {
            flush()
        } finally {
            closeDestination() // TODO check what should be done here
        }
    }

    /**
     * Append single UTF-8 character
     */
    override fun append(c: Char): AbstractOutput {
        val tailPosition = tailPosition
        if (tailEndExclusive - tailPosition >= 3) {
            val size = tailMemory.putUtf8Char(tailPosition, c.toInt())
            this.tailPosition = tailPosition + size
            return this
        }

        appendCharFallback(c)
        return this
    }

    private fun appendCharFallback(c: Char) {
        write(3) { buffer ->
            val size = buffer.memory.putUtf8Char(buffer.writePosition, c.toInt())
            buffer.commitWritten(size)
            size
        }
    }

    override fun append(csq: CharSequence?): AbstractOutput {
        if (csq == null) {
            append("null", 0, 4)
        } else {
            append(csq, 0, csq.length)
        }
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): AbstractOutput {
        if (csq == null) {
            return append("null", start, end)
        }

        writeText(csq, start, end, Charsets.UTF_8)

        return this
    }

    /**
     * Writes another packet to the end. Please note that the instance [p] gets consumed so you don't need to release it
     */
    public fun writePacket(p: ByteReadPacket) {
        val foreignStolen = p.stealAll()
        if (foreignStolen == null) {
            p.release()
            return
        }

        val _tail = _tail
        if (_tail == null) {
            appendChain(foreignStolen)
            return
        }

        writePacketMerging(_tail, foreignStolen, p.pool)
    }

    /**
     * Write chunk buffer to current [Output]. Assuming that chunk buffer is from current pool.
     */
    internal fun writeChunkBuffer(chunkBuffer: ChunkBuffer) {
        val _tail = _tail
        if (_tail == null) {
            appendChain(chunkBuffer)
            return
        }

        writePacketMerging(_tail, chunkBuffer, pool)
    }

    private fun writePacketMerging(tail: ChunkBuffer, foreignStolen: ChunkBuffer, pool: ObjectPool<ChunkBuffer>) {
        tail.commitWrittenUntilIndex(tailPosition)

        val lastSize = tail.readRemaining
        val nextSize = foreignStolen.readRemaining

        // at first we evaluate if it is reasonable to merge chunks
        val maxCopySize = PACKET_MAX_COPY_SIZE
        val appendSize = if (nextSize < maxCopySize && nextSize <= (tail.endGap + tail.writeRemaining)) {
            nextSize
        } else -1

        val prependSize =
            if (lastSize < maxCopySize && lastSize <= foreignStolen.startGap && foreignStolen.isExclusivelyOwned()) {
                lastSize
            } else -1

        if (appendSize == -1 && prependSize == -1) {
            // simply enqueue if there is no reason to merge
            appendChain(foreignStolen)
        } else if (prependSize == -1 || appendSize <= prependSize) {
            // do append
            tail.writeBufferAppend(foreignStolen, tail.writeRemaining + tail.endGap)
            afterHeadWrite()
            foreignStolen.cleanNext()?.let { next ->
                appendChain(next)
            }

            foreignStolen.release(pool)
        } else if (appendSize == -1 || prependSize < appendSize) {
            writePacketSlowPrepend(foreignStolen, tail)
        } else {
            throw IllegalStateException("prep = $prependSize, app = $appendSize")
        }
    }

    /**
     * Do prepend current [tail] to the beginning of [foreignStolen].
     */
    private fun writePacketSlowPrepend(foreignStolen: ChunkBuffer, tail: ChunkBuffer) {
        foreignStolen.writeBufferPrepend(tail)

        val _head = _head ?: error("head should't be null since it is already handled in the fast-path")
        if (_head === tail) {
            this._head = foreignStolen
        } else {
            // we need to fix next reference of the previous chunk before the tail
            // we have to traverse from the beginning to find it
            var pre = _head
            while (true) {
                val next = pre.next!!
                if (next === tail) break
                pre = next
            }

            pre.next = foreignStolen
        }

        tail.release(pool)

        this._tail = foreignStolen.findTail()
    }

    /**
     * Write exact [n] bytes from packet to the builder
     */
    public fun writePacket(p: ByteReadPacket, n: Int) {
        var remaining = n

        while (remaining > 0) {
            val headRemaining = p.headRemaining
            if (headRemaining <= remaining) {
                remaining -= headRemaining
                appendSingleChunk(p.steal() ?: throw EOFException("Unexpected end of packet"))
            } else {
                p.read { view ->
                    writeFully(view, remaining)
                }
                break
            }
        }
    }

    /**
     * Write exact [n] bytes from packet to the builder
     */
    public fun writePacket(p: ByteReadPacket, n: Long) {
        var remaining = n

        while (remaining > 0L) {
            val headRemaining = p.headRemaining.toLong()
            if (headRemaining <= remaining) {
                remaining -= headRemaining
                appendSingleChunk(p.steal() ?: throw EOFException("Unexpected end of packet"))
            } else {
                p.read { view ->
                    writeFully(view, remaining.toInt())
                }
                break
            }
        }
    }

    override fun append(csq: CharArray, start: Int, end: Int): Appendable {
        writeText(csq, start, end, Charsets.UTF_8)
        return this
    }

    private inline fun appendCharsTemplate(
        start: Int,
        end: Int,
        block: Buffer.(index: Int) -> Int
    ): Int {
        var idx = start
        if (idx >= end) return idx
        idx = prepareWriteHead(1).block(idx)
        afterHeadWrite()

        while (idx < end) {
            idx = appendNewChunk().block(idx)
            afterHeadWrite()
        }

        return idx
    }

    @Deprecated("Use writeText instead", ReplaceWith("writeText(s)"))
    public fun writeStringUtf8(s: String) {
        writeText(s)
    }

    @Deprecated("Use writeText instead", ReplaceWith("this.writeText(cs)"))
    public fun writeStringUtf8(cs: CharSequence) {
        writeText(cs)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Buffer.putUtf8Char(v: Int) = when {
        v in 1..0x7f -> {
            writeByte(v.toByte())
            1
        }
        v > 0x7ff -> {
            writeExact(3, "3 bytes character") { memory, offset ->
                memory[offset] = (0xe0 or ((v shr 12) and 0x0f)).toByte()
                memory[offset + 1] = (0x80 or ((v shr 6) and 0x3f)).toByte()
                memory[offset + 2] = (0x80 or (v and 0x3f)).toByte()
            }
            3
        }
        else -> {
            writeExact(2, "2 bytes character") { memory, offset ->
                memory[offset] = (0xc0 or ((v shr 6) and 0x1f)).toByte()
                memory[offset + 1] = (0x80 or (v and 0x3f)).toByte()
            }
            2
        }
    }

    /**
     * Release any resources that the builder holds. Builder shouldn't be used after release
     */
    public final fun release() {
        close()
    }

    @DangerousInternalIoApi
    public fun prepareWriteHead(n: Int): ChunkBuffer {
        if (tailRemaining >= n) {
            _tail?.let {
                it.commitWrittenUntilIndex(tailPosition)
                return it
            }
        }
        return appendNewChunk()
    }

    @DangerousInternalIoApi
    public fun afterHeadWrite() {
        _tail?.let { tailPosition = it.writePosition }
    }

    @PublishedApi
    internal inline fun write(size: Int, block: (Buffer) -> Int): Int {
        val buffer = prepareWriteHead(size)
        try {
            val result = block(buffer)
            check(result >= 0) { "The returned value shouldn't be negative" }

            return result
        } finally {
            afterHeadWrite()
        }
    }

    @PublishedApi
    @Deprecated("There is no need to do that anymore.", level = DeprecationLevel.HIDDEN)
    internal fun addSize(n: Int) {
        check(n >= 0) { "It should be non-negative size increment: $n" }
        check(n <= tailRemaining) { "Unable to mark more bytes than available: $n > $tailRemaining" }

        // For binary compatibility we need to update pointers
        tailPosition += n
    }

    @Suppress("DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    internal open fun last(buffer: IoBuffer) {
        appendSingleChunk(buffer as ChunkBuffer)
    }

    @Suppress("DEPRECATION")
    @Deprecated(
        "Use appendNewChunk instead",
        replaceWith = ReplaceWith("appendNewChunk()"),
        level = DeprecationLevel.HIDDEN
    )
    public fun appendNewBuffer(): IoBuffer = appendNewChunk() as IoBuffer

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public open fun reset() {
    }
}
