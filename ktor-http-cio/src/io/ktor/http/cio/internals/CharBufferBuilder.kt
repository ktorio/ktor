package io.ktor.http.cio.internals

import kotlinx.io.pool.*
import java.nio.*

@Suppress("LoopToCallChain", "ReplaceRangeToWithUntil")
internal class CharBufferBuilder(val pool: ObjectPool<CharBuffer> = CharBufferPool) : CharSequence, Appendable {
    private var buffers: MutableList<CharBuffer>? = null
    private var current: CharBuffer? = null
    private var stringified: String? = null

    override var length: Int = 0
        private set

    override fun get(index: Int): Char {
        require(index >= 0) { "index is negative: $index"}
        require(index < length) { "index $index is not in range [0, $length)" }

        return getImpl(index)
    }

    private fun getImpl(index: Int) = bufferForIndex(index).get(index % CHAR_BUFFER_LENGTH)

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex <= endIndex) { "startIndex ($startIndex) should be less or equal to endIndex ($endIndex)" }
        require(startIndex >= 0) { "startIndex is negative: $startIndex" }
        require(endIndex <= length) { "endIndex ($endIndex) is greater than length ($length)" }

        return SubSequenceImpl(startIndex, endIndex)
    }

    override fun toString() = stringified ?: copy(0, length).toString().also { stringified = it }

    override fun equals(other: Any?): Boolean {
        if (other !is CharSequence) return false
        if (length != other.length) return false

        return rangeEqualsImpl(0, other, 0, length)
    }

    override fun hashCode() = stringified?.hashCode() ?: hashCodeImpl(0, length)

    override fun append(c: Char): Appendable {
        nonFullBuffer().put(c)
        stringified = null
        length++
        return this
    }

    override fun append(csq: CharSequence, start: Int, end: Int): Appendable {
        append(csq, start, end, nonFullBuffer())
        stringified = null
        length += end - start
        return this
    }

    override fun append(csq: CharSequence): java.lang.Appendable {
        append(csq, 0, csq.length, nonFullBuffer())
        stringified = null
        length += csq.length
        return this
    }

    fun release() {
        length = 0
        val list = buffers
        buffers = null

        if (list != null) {
            current = null
            for (i in 0 until list.size) {
                pool.recycle(list[i])
            }
        } else {
            current?.let { pool.recycle(it) }
            current = null
        }

        stringified = null
    }

    private fun copy(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex == endIndex) return ""

        val builder = StringBuilder(endIndex - startIndex)

        var buffer: CharBuffer
        var base = startIndex - (startIndex % CHAR_BUFFER_LENGTH)

        while (base < endIndex) {
            buffer = bufferForIndex(base)
            val innerStartIndex = maxOf(0, startIndex - base)
            val innerEndIndex = minOf(endIndex - base, CHAR_BUFFER_LENGTH)

            for (innerIndex in innerStartIndex until innerEndIndex) {
                builder.append(buffer.get(innerIndex))
            }

            base += CHAR_BUFFER_LENGTH
        }

        return builder
    }

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    private inner class SubSequenceImpl(val start: Int, val end: Int) : CharSequence {
        private var stringified: String? = null

        override val length: Int
            get() = end - start

        override fun get(index: Int): Char {
            val withOffset = index + start
            require(index >= 0) { "index is negative: $index" }
            require(withOffset < end) { "index ($index) should be less than length ($length)" }

            return this@CharBufferBuilder.getImpl(withOffset)
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            require(startIndex >= 0) { "start is negative: $startIndex" }
            require(startIndex <= endIndex) { "start ($startIndex) should be less or equal to end ($endIndex)" }
            require(endIndex <= end - start) { "end should be less than length ($length)" }
            if (startIndex == endIndex) return ""

            return SubSequenceImpl(start + startIndex, start + endIndex)
        }

        override fun toString() = stringified ?: copy(start, end).toString().also { stringified = it }

        override fun equals(other: Any?): Boolean {
            if (other !is CharSequence) return false
            if (other.length != length) return false

            return rangeEqualsImpl(start, this@CharBufferBuilder, 0, length)
        }

        override fun hashCode() = stringified?.hashCode() ?: hashCodeImpl(start, end)
    }

    private tailrec fun append(csq: CharSequence, start: Int, end: Int, buffer: CharBuffer) {
        val limitedEnd = minOf(end, start + buffer.remaining())

        for (i in start until limitedEnd) {
            buffer.put(csq[i])
        }

        if (limitedEnd < end) {
            return append(csq, limitedEnd, end, appendNewBuffer())
        }
    }

    private fun bufferForIndex(index: Int): CharBuffer {
        val list = buffers

        if (list == null) {
            if (index >= CHAR_BUFFER_LENGTH) throwSingleBuffer(index)
            return current ?: throwSingleBuffer(index)
        }

        return list[index / CHAR_BUFFER_LENGTH]
    }

    private fun throwSingleBuffer(index: Int): Nothing = throw IndexOutOfBoundsException("$index is not in range [0; ${current?.position() ?: 0})")

    private fun nonFullBuffer(): CharBuffer {
        return current?.takeIf { it.hasRemaining() } ?: appendNewBuffer()
    }

    private fun appendNewBuffer(): CharBuffer {
        val newBuffer = pool.borrow()
        val existing = current
        current = newBuffer

        if (existing != null) {
            val list = buffers ?: ArrayList<CharBuffer>().also {
                buffers = it
                it.add(existing)
            }

            list.add(newBuffer)
        }

        return newBuffer
    }

    private fun rangeEqualsImpl(start: Int, other: CharSequence, otherStart: Int, length: Int): Boolean {
        for (i in 0 until length) {
            if (getImpl(start + i) != other[otherStart + i]) return false
        }

        return true
    }

    private fun hashCodeImpl(start: Int, end: Int): Int {
        var hc = 0
        for (i in start until end) {
            hc = 31 * hc + getImpl(i).toInt()
        }

        return hc
    }
}