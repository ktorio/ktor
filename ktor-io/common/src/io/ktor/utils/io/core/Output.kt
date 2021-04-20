package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * This shouldn't be implemented directly. Inherit [Output] instead.
 */
public abstract class Output internal constructor(
    private val headerSizeHint: Int = 0,
    protected val pool: ObjectPool<ChunkBuffer>
) : Appendable, Closeable {
    public constructor(pool: ObjectPool<ChunkBuffer>): this(0, pool)

    public constructor() : this(ChunkBuffer.Pool)

    protected val _size: Int
        get() = chainedSize + (tailPosition - tailInitialPosition)

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

    private val state = OutputSharedState()

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

    public fun flush() {
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

    internal fun appendSingleChunk(buffer: ChunkBuffer) {
        check(buffer.next == null) { "It should be a single buffer chunk." }
        appendChainImpl(buffer, buffer, 0)
    }

    internal fun appendChain(head: ChunkBuffer) {
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

    private fun appendChainImpl(head: ChunkBuffer, newTail: ChunkBuffer, chainedSizeDelta: Int) {
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

    public fun writeByte(v: Byte) {
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
    override fun append(value: Char): Output {
        val tailPosition = tailPosition
        if (tailEndExclusive - tailPosition >= 3) {
            val size = tailMemory.putUtf8Char(tailPosition, value.toInt())
            this.tailPosition = tailPosition + size
            return this
        }

        appendCharFallback(value)
        return this
    }

    private fun appendCharFallback(c: Char) {
        write(3) { buffer ->
            val size = buffer.memory.putUtf8Char(buffer.writePosition, c.toInt())
            buffer.commitWritten(size)
            size
        }
    }

    override fun append(value: CharSequence?): Output {
        if (value == null) {
            append("null", 0, 4)
        } else {
            append(value, 0, value.length)
        }
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Output {
        if (value == null) {
            return append("null", startIndex, endIndex)
        }

        writeText(value, startIndex, endIndex, Charsets.UTF_8)

        return this
    }

    /**
     * Writes another packet to the end. Please note that the instance [packet] gets consumed so you don't need to release it
     */
    public fun writePacket(packet: ByteReadPacket) {
        val foreignStolen = packet.stealAll()
        if (foreignStolen == null) {
            packet.release()
            return
        }

        val _tail = _tail
        if (_tail == null) {
            appendChain(foreignStolen)
            return
        }

        writePacketMerging(_tail, foreignStolen, packet.pool)
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

    public fun append(csq: CharArray, start: Int, end: Int): Appendable {
        writeText(csq, start, end, Charsets.UTF_8)
        return this
    }

    /**
     * Release any resources that the builder holds. Builder shouldn't be used after release
     */
    public fun release() {
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

    internal open fun last(buffer: ChunkBuffer) {
        appendSingleChunk(buffer)
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
}


public fun Output.append(csq: CharSequence, start: Int = 0, end: Int = csq.length): Appendable {
    return append(csq, start, end)
}

public fun Output.append(csq: CharArray, start: Int = 0, end: Int = csq.size): Appendable {
    return append(csq, start, end)
}

public fun Output.writeFully(src: ByteArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyBytesTemplate(offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: ShortArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(2, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: IntArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(4, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: LongArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(8, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: FloatArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(4, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: DoubleArray, offset: Int = 0, length: Int = src.size - offset) {
    writeFullyTemplate(8, offset, length) { buffer, currentOffset, count ->
        buffer.writeFully(src, currentOffset, count)
    }
}

public fun Output.writeFully(src: Buffer, length: Int = src.readRemaining) {
    writeFullyBytesTemplate(0, length) { buffer, _, count ->
        buffer.writeFully(src, count)
    }
}

public fun Output.writeFully(src: Memory, offset: Int, length: Int) {
    writeFully(src, offset.toLong(), length.toLong())
}

public fun Output.writeFully(src: Memory, offset: Long, length: Long) {
    writeFullyBytesTemplate(offset, length) { memory, destinationOffset, sourceOffset, count ->
        src.copyTo(memory, sourceOffset, count, destinationOffset)
    }
}

public fun Output.fill(times: Long, value: Byte = 0) {
    var written = 0L
    writeWhile { buffer ->
        val partTimes = minOf(buffer.writeRemaining.toLong(), times - written).toInt()
        buffer.fill(partTimes, value)
        written += partTimes
        written < times
    }
}

/**
 * Append number of chunks invoking [block] function while the returned value is true.
 * Depending on the output underlying implementation it could invoke [block] function with the same buffer several times
 * however it is guaranteed that it is always non-empty.
 */
@DangerousInternalIoApi
public inline fun Output.writeWhile(block: (Buffer) -> Boolean) {
    var tail: ChunkBuffer = prepareWriteHead(1, null)
    try {
        while (true) {
            if (!block(tail)) break
            tail = prepareWriteHead(1, tail)
        }
    } finally {
        afterHeadWrite()
    }
}

/**
 * Append number of chunks invoking [block] function while the returned value is positive.
 * If returned value is positive then it will be invoked again with a buffer having at least requested number of
 * bytes space (could be the same buffer as before if it complies to the restriction).
 * @param initialSize for the first buffer passed to [block] function
 */
@DangerousInternalIoApi
public inline fun Output.writeWhileSize(initialSize: Int = 1, block: (Buffer) -> Int) {
    var tail = prepareWriteHead(initialSize, null)

    try {
        var size: Int
        while (true) {
            size = block(tail)
            if (size <= 0) break
            tail = prepareWriteHead(size, tail)
        }
    } finally {
        afterHeadWrite()
    }
}

private inline fun Output.writeFullyBytesTemplate(
    offset: Int,
    length: Int,
    block: (Buffer, currentOffset: Int, count: Int) -> Unit
) {
    var currentOffset = offset
    var remaining = length

    writeWhile { buffer ->
        val size = minOf(remaining, buffer.writeRemaining)
        block(buffer, currentOffset, size)
        currentOffset += size
        remaining -= size
        remaining > 0
    }
}

private inline fun Output.writeFullyBytesTemplate(
    initialOffset: Long,
    length: Long,
    block: (destination: Memory, destinationOffset: Long, currentOffset: Long, count: Long) -> Unit
) {
    var currentOffset = initialOffset
    var remaining = length

    writeWhile { buffer ->
        val size = minOf(remaining, buffer.writeRemaining.toLong())
        block(buffer.memory, buffer.writePosition.toLong(), currentOffset, size)
        buffer.commitWritten(size.toInt())
        currentOffset += size
        remaining -= size
        remaining > 0
    }
}

private inline fun Output.writeFullyTemplate(
    componentSize: Int,
    offset: Int,
    length: Int,
    block: (Buffer, currentOffset: Int, count: Int) -> Unit
) {
    var currentOffset = offset
    var remaining = length

    writeWhileSize(componentSize) { buffer ->
        val size = minOf(remaining, buffer.writeRemaining)
        block(buffer, currentOffset, size)
        currentOffset += size
        remaining -= size
        remaining * componentSize
    }
}
