package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

/**
 * Usually shouldn't be implemented directly. Inherit [AbstractInput] instead.
 */
public abstract class Input(
    head: ChunkBuffer = ChunkBuffer.Empty,
    remaining: Long = head.remainingAll(),
    public val pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : Closeable {
    private val state = InputSharedState(head, remaining)

    /**
     * It is `true` when it is known that no more bytes will be available. When it is `false` then this means that
     * it is not known yet or there are available bytes.
     * Please note that `false` value doesn't guarantee that there are available bytes so `readByte()` may fail.
     */
    public val endOfInput: Boolean
        get() = headRemaining == 0 && tailRemaining == 0L && (noMoreChunksAvailable || doFill() == null)

    /**
     * Read the next bytes into the [destination] starting at [offset] at most [length] bytes.
     * May block until at least one byte is available.
     * Usually bypass all exceptions from the underlying source.
     *
     * @param offset in bytes where result should be written
     * @param length should be at least one byte
     *
     * @return number of bytes were copied or `0` if EOF encountered
     */
    protected abstract fun fill(destination: Memory, offset: Int, length: Int): Int

    /**
     * Should close the underlying bytes source. Could do nothing or throw exceptions.
     */
    protected abstract fun closeSource()

    /**
     * Current head chunk reference
     */
    private var _head: ChunkBuffer
        get() = state.head
        set(newHead) {
            state.head = newHead
            state.headMemory = newHead.memory
            state.headPosition = newHead.readPosition
            state.headEndExclusive = newHead.writePosition
        }

    @PublishedApi
    internal val head: ChunkBuffer
        get() = _head.also { it.discardUntilIndex(headPosition) }

    @PublishedApi
    internal var headMemory: Memory
        get() = state.headMemory
        set(value) {
            state.headMemory = value
        }

    @PublishedApi
    internal var headPosition: Int
        get() = state.headPosition
        set(value) {
            state.headPosition = value
        }

    @PublishedApi
    internal var headEndExclusive: Int
        get() = state.headEndExclusive
        set(value) {
            state.headEndExclusive = value
        }

    @PublishedApi
    @Suppress("DEPRECATION_ERROR")
    internal var headRemaining: Int
        inline get() = headEndExclusive - headPosition
        @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
        set(newRemaining) {
            updateHeadRemaining(newRemaining)
        }

    private var tailRemaining: Long
        get() = state.tailRemaining
        set(newValue) {
            require(newValue >= 0) { "tailRemaining shouldn't be negative: $newValue" }
            state.tailRemaining = newValue
        }

    internal fun prefetch(min: Long): Boolean {
        if (min <= 0) return true
        val headRemaining = headRemaining
        if (headRemaining >= min || headRemaining + tailRemaining >= min) return true

        return doPrefetch(min)
    }

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
    ): Long {
        prefetch(min + offset)

        var current: ChunkBuffer = head
        var copied = 0L
        var skip = offset
        var writePosition = destinationOffset
        val maxCopySize = minOf(max, destination.size - destinationOffset)

        while (copied < min && copied < maxCopySize) {
            val chunkSize = current.readRemaining
            if (chunkSize > skip) {
                val size = minOf(chunkSize - skip, maxCopySize - copied)
                current.memory.copyTo(
                    destination,
                    current.readPosition + skip,
                    size,
                    writePosition
                )
                skip = 0
                copied += size
                writePosition += size
            } else {
                skip -= chunkSize
            }

            current = current.next ?: break
        }

        return copied
    }

    /**
     * @see doFill for similar logic
     * @see appendView for similar logic
     */
    private fun doPrefetch(min: Long): Boolean {
        var tail = _head.findTail()
        var available = headRemaining + tailRemaining

        do {
            val next = fill()
            if (next == null) {
                noMoreChunksAvailable = true
                return false
            }

            val chunkSize = next.readRemaining
            if (tail === ChunkBuffer.Empty) {
                _head = next
                tail = next
            } else {
                tail.next = next
                tailRemaining += chunkSize
            }

            available += chunkSize
        } while (available < min)

        return true
    }

    /**
     * Number of bytes available for read
     */
    public val remaining: Long get() = headRemaining.toLong() + tailRemaining

    /**
     * @return `true` if there is at least one byte to read
     */
    public fun canRead(): Boolean = headPosition != headEndExclusive || tailRemaining != 0L

    /**
     * @return `true` if there are at least [n] bytes to read
     */
    public fun hasBytes(n: Int): Boolean = headRemaining + tailRemaining >= n

    private var noMoreChunksAvailable = false

    /**
     * Release packet. After this function invocation the packet becomes empty.
     * If it has been copied via [ByteReadPacket.copy]
     * then the copy should be released as well.
     */
    public fun release() {
        val head = head
        val empty = ChunkBuffer.Empty

        if (head !== empty) {
            this._head = empty
            tailRemaining = 0
            head.releaseAll(pool)
        }
    }

    /**
     * Close input including the underlying source. All pending bytes will be discarded.
     * It is not recommended to invoke it with read operations in-progress concurrently.
     */
    override fun close() {
        release()
        if (!noMoreChunksAvailable) {
            noMoreChunksAvailable = true
        }
        closeSource()
    }

    internal fun stealAll(): ChunkBuffer? {
        val head = head
        val empty = ChunkBuffer.Empty

        if (head === empty) return null
        this._head = empty
        tailRemaining = 0
        return head
    }

    internal fun steal(): ChunkBuffer? {
        val head = head
        val next = head.next
        val empty = ChunkBuffer.Empty
        if (head === empty) return null

        if (next == null) {
            this._head = empty
            this.tailRemaining = 0
        } else {
            this._head = next
            this.tailRemaining -= next.readRemaining
        }

        head.next = null
        return head
    }

    internal fun append(chain: ChunkBuffer) {
        if (chain === ChunkBuffer.Empty) return

        val size = chain.remainingAll()
        if (_head === ChunkBuffer.Empty) {
            _head = chain
            tailRemaining = size - headRemaining
        } else {
            _head.findTail().next = chain
            tailRemaining += size
        }
    }

    internal fun tryWriteAppend(chain: ChunkBuffer): Boolean {
        val tail = head.findTail()
        val size = chain.readRemaining

        if (size == 0 || tail.writeRemaining < size) return false
        tail.writeBufferAppend(chain, size)

        if (head === tail) {
            headEndExclusive = tail.writePosition
        } else {
            tailRemaining += size
        }

        return true
    }

    /**
     * Read the next upcoming byte
     * @throws EOFException if no more bytes available.
     */
    public fun readByte(): Byte {
        val index = headPosition
        val nextIndex = index + 1
        if (nextIndex < headEndExclusive) {
            // fast-path when we are not reading the last byte
            headPosition = nextIndex
            return headMemory[index]
        }

        return readByteSlow()
    }

    private fun readByteSlow(): Byte {
        val index = headPosition
        if (index < headEndExclusive) {
            val value = headMemory[index]
            headPosition = index
            val head = _head
            head.discardUntilIndex(index)
            ensureNext(head)
            return value
        }

        val head = prepareRead(1) ?: prematureEndOfStream(1)
        val byte = head.readByte()
        completeReadHead(head)
        return byte
    }

    /**
     * Discards at most [n] bytes
     * @return number of bytes has been discarded
     */
    public fun discard(n: Int): Int {
        require(n >= 0) { "Negative discard is not allowed: $n" }
        return discardAsMuchAsPossible(n, 0)
    }

    /**
     * Discards exactly [n] bytes or fails with [EOFException]
     */
    public fun discardExact(n: Int) {
        if (discard(n) != n) throw EOFException("Unable to discard $n bytes due to end of packet")
    }

    /**
     * Returns next byte (unsigned) or `-1` if no more bytes available
     */
    public fun tryPeek(): Int {
        val head = head
        if (headRemaining > 0) {
            return head.tryPeekByte()
        }

        if (tailRemaining == 0L && noMoreChunksAvailable) return -1

        return prepareReadLoop(1, head)?.tryPeekByte() ?: -1
    }

    public fun peekTo(buffer: ChunkBuffer): Int {
        val head = prepareReadHead(1) ?: return -1

        val size = minOf(buffer.writeRemaining, head.readRemaining)
        (buffer as Buffer).writeFully(head, size)

        return size
    }

    /**
     * Discard at most [n] bytes
     */
    public fun discard(n: Long): Long {
        if (n <= 0) return 0L
        return discardAsMuchAsPossible(n, 0)
    }

    internal fun readAvailableCharacters(destination: CharArray, off: Int, len: Int): Int {
        if (endOfInput) return -1

        val out = object : Appendable {
            private var idx = off

            override fun append(value: Char): Appendable {
                destination[idx++] = value
                return this
            }

            override fun append(value: CharSequence?): Appendable {
                if (value is String) {
                    value.getCharsInternal(destination, idx)
                    idx += value.length
                } else if (value != null) {
                    for (i in 0 until value.length) {
                        destination[idx++] = value[i]
                    }
                }

                return this
            }

            override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
                throw UnsupportedOperationException()
            }
        }

        return readText(out, 0, len)
    }

    /**
     * Read at least [min] and at most [max] characters and append them to [out]
     * @return number of characters appended
     */
    public fun readText(out: Appendable, min: Int = 0, max: Int = Int.MAX_VALUE): Int {
        if (max.toLong() >= remaining) {
            val s = readTextExactBytes(bytesCount = remaining.toInt())
            out.append(s)
            return s.length
        }
        return readASCII(out, min, max)
    }

    /**
     * Read exactly [exactCharacters] characters and append them to [out]
     */
    public fun readTextExact(out: Appendable, exactCharacters: Int) {
        readText(out, exactCharacters, exactCharacters)
    }

    /**
     * Read a string at last [min] and at most [max] characters length
     */
    public fun readText(min: Int = 0, max: Int = Int.MAX_VALUE): String {
        if (min == 0 && (max == 0 || endOfInput)) return ""
        val remaining = remaining
        if (remaining > 0 && max.toLong() >= remaining) return readTextExactBytes(bytesCount = remaining.toInt())

        return buildString(min.coerceAtLeast(16).coerceAtMost(max)) {
            readASCII(this, min, max)
        }
    }

    /**
     * Read a string exactly [exactCharacters] length
     */
    public fun readTextExact(exactCharacters: Int): String {
        return readText(exactCharacters, exactCharacters)
    }

    private fun readASCII(out: Appendable, min: Int, max: Int): Int {
        when {
            max == 0 && min == 0 -> return 0
            endOfInput -> if (min == 0) return 0 else atLeastMinCharactersRequire(min)
            max < min -> minShouldBeLess(min, max)
        }

        var copied = 0
        var utf8 = false

        takeWhile { buffer ->
            val rc = buffer.decodeASCII {
                if (copied == max) false
                else {
                    out.append(it)
                    copied++
                    true
                }
            }

            when {
                rc -> true
                copied == max -> false
                else -> {
                    utf8 = true
                    false
                }
            }
        }

        if (utf8) {
            return copied + readUtf8(out, min - copied, max - copied)
        }
        if (copied < min) prematureEndOfStreamChars(min, copied)
        return copied
    }

    private fun atLeastMinCharactersRequire(min: Int): Nothing =
        throw EOFException("at least $min characters required but no bytes available")

    private fun minShouldBeLess(min: Int, max: Int): Nothing =
        throw IllegalArgumentException("min should be less or equal to max but min = $min, max = $max")

    private fun prematureEndOfStreamChars(min: Int, copied: Int): Nothing = throw MalformedUTF8InputException(
        "Premature end of stream: expected at least $min chars but had only $copied"
    )

    private fun readUtf8(out: Appendable, min: Int, max: Int): Int {
        var copied = 0

        takeWhileSize { buffer ->
            val size = buffer.decodeUTF8 {
                if (copied == max) false
                else {
                    out.append(it)
                    copied++
                    true
                }
            }

            when {
                size == 0 -> 1
                size > 0 -> size
                else -> 0
            }
        }

        if (copied < min) prematureEndOfStreamChars(min, copied)

        return copied
    }

    private tailrec fun discardAsMuchAsPossible(n: Long, skipped: Long): Long {
        if (n == 0L) return skipped
        val current = prepareRead(1) ?: return skipped
        val size = minOf(current.readRemaining.toLong(), n).toInt()
        current.discardExact(size)
        headPosition += size
        afterRead(current)

        return discardAsMuchAsPossible(n - size, skipped + size)
    }

    private fun discardAsMuchAsPossible(n: Int, skipped: Int): Int {
        var currentCount = n
        var currentSkipped = skipped

        while (true) {
            if (currentCount == 0) {
                return currentSkipped
            }

            val current = prepareRead(1) ?: return currentSkipped

            val size = minOf(current.readRemaining, currentCount)
            current.discardExact(size)
            headPosition += size
            afterRead(current)

            currentCount -= size
            currentSkipped += size
        }
    }

    private tailrec fun readAsMuchAsPossible(array: ByteArray, offset: Int, length: Int, copied: Int): Int {
        if (length == 0) return copied
        val current = prepareRead(1) ?: return copied
        val size = minOf(length, current.readRemaining)

        current.readFully(array, offset, size)
        headPosition = current.readPosition

        return if (size != length || current.readRemaining == 0) {
            afterRead(current)
            readAsMuchAsPossible(array, offset + size, length - size, copied + size)
        } else {
            copied + size
        }
    }

    private fun notEnoughBytesAvailable(n: Int): Nothing {
        throw EOFException("Not enough data in packet ($remaining) to read $n byte(s)")
    }

    @Deprecated("Not supported anymore.", level = DeprecationLevel.ERROR)
    public fun updateHeadRemaining(remaining: Int) {
        // the only external usages are from readDirect
        // so after using head chunk directly we should fix positions instead
        val newPosition = headEndExclusive - remaining

        if (newPosition < 0) {
            throw IllegalArgumentException("Unable to update position to negative. newRemaining is too big.")
        }

        headPosition = newPosition
    }

    @DangerousInternalIoApi
    public fun prepareReadHead(minSize: Int): ChunkBuffer? = prepareReadLoop(minSize, head)

    @DangerousInternalIoApi
    public fun ensureNextHead(current: ChunkBuffer): ChunkBuffer? = ensureNext(current)

    @PublishedApi
    internal fun ensureNext(current: ChunkBuffer): ChunkBuffer? = ensureNext(
        current,
        ChunkBuffer.Empty
    )

    @DangerousInternalIoApi
    public fun fixGapAfterRead(current: ChunkBuffer) {
        val next = current.next ?: return fixGapAfterReadFallback(current)

        val remaining = current.readRemaining
        val overrunSize = minOf(remaining, Buffer.ReservedSize - current.endGap)
        if (next.startGap < overrunSize) {
            return fixGapAfterReadFallback(current)
        }

        next.restoreStartGap(overrunSize)

        if (remaining > overrunSize) {
            current.releaseEndGap()

            this.headEndExclusive = current.writePosition
            this.tailRemaining += overrunSize
        } else {
            this._head = next
            this.tailRemaining -= next.readRemaining - overrunSize
            current.cleanNext()
            current.release(pool)
        }
    }

    private fun fixGapAfterReadFallback(current: ChunkBuffer) {
        if (noMoreChunksAvailable && current.next == null) {
            this.headPosition = current.readPosition
            this.headEndExclusive = current.writePosition
            this.tailRemaining = 0
            return
        }

        val size = current.readRemaining
        val overrun = minOf(size, Buffer.ReservedSize - current.endGap)

        if (size > overrun) {
            fixGapAfterReadFallbackUnreserved(current, size, overrun)
        } else {
            val new = pool.borrow()
            new.reserveEndGap(Buffer.ReservedSize)
            new.next = current.cleanNext()

            new.writeBufferAppend(current, size)
            this._head = new
        }

        current.release(pool)
    }

    private fun fixGapAfterReadFallbackUnreserved(current: ChunkBuffer, size: Int, overrun: Int) {
        // if we have a chunk with no end reservation
        // we can split it into two to fix it

        val chunk1 = pool.borrow()
        val chunk2 = pool.borrow()

        chunk1.reserveEndGap(Buffer.ReservedSize)
        chunk2.reserveEndGap(Buffer.ReservedSize)
        chunk1.next = chunk2
        chunk2.next = current.cleanNext()

        chunk1.writeBufferAppend(current, size - overrun)
        chunk2.writeBufferAppend(current, overrun)

        this._head = chunk1
        this.tailRemaining = chunk2.remainingAll()
    }

    private tailrec fun ensureNext(current: ChunkBuffer, empty: ChunkBuffer): ChunkBuffer? {
        if (current === empty) {
            return doFill()
        }

        val next = current.cleanNext()
        current.release(pool)

        return when {
            next == null -> {
                this._head = empty
                this.tailRemaining = 0L
                ensureNext(empty, empty)
            }
            next.canRead() -> {
                _head = next
                tailRemaining -= next.readRemaining
                next
            }
            else -> ensureNext(next, empty)
        }
    }

    /**
     * Reads the next chunk suitable for reading or `null` if no more chunks available. It is also allowed
     * to return a chain of chunks linked through [ChunkBuffer.next]. The last chunk should have `null` next reference.
     * Could rethrow exceptions from the underlying source.
     */
    protected open fun fill(): ChunkBuffer? {
        val buffer = pool.borrow()
        try {
            buffer.reserveEndGap(Buffer.ReservedSize)
            val copied = fill(buffer.memory, buffer.writePosition, buffer.writeRemaining)

            if (copied == 0) {
                noMoreChunksAvailable = true

                if (!buffer.canRead()) {
                    buffer.release(pool)
                    return null
                }
            }

            buffer.commitWritten(copied)

            return buffer
        } catch (t: Throwable) {
            buffer.release(pool)
            throw t
        }
    }

    protected fun markNoMoreChunksAvailable() {
        if (!noMoreChunksAvailable) {
            noMoreChunksAvailable = true
        }
    }

    /**
     * see [prefetch] for similar logic
     */
    private fun doFill(): ChunkBuffer? {
        if (noMoreChunksAvailable) return null
        val chunk = fill()
        if (chunk == null) {
            noMoreChunksAvailable = true
            return null
        }
        appendView(chunk)
        return chunk
    }

    private fun appendView(chunk: ChunkBuffer) {
        val tail = _head.findTail()
        if (tail === ChunkBuffer.Empty) {
            _head = chunk
            require(tailRemaining == 0L) {
                throw IllegalStateException("It should be no tail remaining bytes if current tail is EmptyBuffer")
            }
            tailRemaining = chunk.next?.remainingAll() ?: 0L
        } else {
            tail.next = chunk
            tailRemaining += chunk.remainingAll()
        }
    }

    @PublishedApi
    internal fun prepareRead(minSize: Int): ChunkBuffer? {
        val head = head
        if (headEndExclusive - headPosition >= minSize) return head
        return prepareReadLoop(minSize, head)
    }

    @PublishedApi
    internal fun prepareRead(minSize: Int, head: ChunkBuffer): ChunkBuffer? {
        if (headEndExclusive - headPosition >= minSize) return head
        return prepareReadLoop(minSize, head)
    }

    private tailrec fun prepareReadLoop(minSize: Int, head: ChunkBuffer): ChunkBuffer? {
        val headSize = headRemaining
        if (headSize >= minSize) return head

        val next = head.next ?: doFill() ?: return null

        if (headSize == 0) {
            if (head !== ChunkBuffer.Empty) {
                releaseHead(head)
            }

            return prepareReadLoop(minSize, next)
        } else {
            val desiredExtraBytes = minSize - headSize
            val copied = head.writeBufferAppend(next, desiredExtraBytes)
            headEndExclusive = head.writePosition
            tailRemaining -= copied
            if (!next.canRead()) {
                head.next = null
                head.next = next.cleanNext()
                next.release(pool)
            } else {
                next.reserveStartGap(copied)
            }
        }

        if (head.readRemaining >= minSize) return head
        if (minSize > Buffer.ReservedSize) minSizeIsTooBig(minSize)

        return prepareReadLoop(minSize, head)
    }

    private fun minSizeIsTooBig(minSize: Int): Nothing {
        throw IllegalStateException("minSize of $minSize is too big (should be less than ${Buffer.ReservedSize})")
    }

    private fun afterRead(head: ChunkBuffer) {
        if (head.readRemaining == 0) {
            releaseHead(head)
        }
    }

    internal fun releaseHead(head: ChunkBuffer): ChunkBuffer {
        val next = head.cleanNext() ?: ChunkBuffer.Empty
        this._head = next
        this.tailRemaining -= next.readRemaining
        head.release(pool)

        return next
    }

    public companion object
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
