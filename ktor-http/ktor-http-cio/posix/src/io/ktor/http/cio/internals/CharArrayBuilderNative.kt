// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.util.collections.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlin.math.*
import kotlin.native.concurrent.*

@Suppress("LoopToCallChain", "ReplaceRangeToWithUntil", "KDocMissingDocumentation")
internal actual class CharArrayBuilder actual constructor(pool: ObjectPool<CharArray>) : CharSequence, Appendable {
    private var buffers: MutableList<MutableData>? by shared(null)
    private var current: MutableData? by shared(null)
    private var stringified: String? by shared(null)
    private var released = atomic(false)
    private var remaining: Int = 0

    actual override var length: Int = 0
        private set

    actual override fun get(index: Int): Char {
        require(index >= 0) { "index is negative: $index" }
        require(index < length) { "index $index is not in range [0, $length)" }

        return getImpl(index)
    }

    private fun getImpl(index: Int): Char = bufferForIndex(index)[index % CHAR_BUFFER_ARRAY_LENGTH].toInt().toChar()

    actual override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex <= endIndex) { "startIndex ($startIndex) should be less or equal to endIndex ($endIndex)" }
        require(startIndex >= 0) { "startIndex is negative: $startIndex" }
        require(endIndex <= length) { "endIndex ($endIndex) is greater than length ($length)" }

        return SubSequenceImpl(startIndex, endIndex)
    }

    override fun toString(): String = stringified ?: copy(0, length).toString().also { stringified = it }

    override fun equals(other: Any?): Boolean {
        if (other !is CharSequence) return false
        if (length != other.length) return false

        return rangeEqualsImpl(0, other, 0, length)
    }

    override fun hashCode(): Int = stringified?.hashCode() ?: hashCodeImpl(0, length)

    actual override fun append(value: Char): Appendable {
        nonFullBuffer().withBufferLocked { array, _ ->
            array[CHAR_BUFFER_ARRAY_LENGTH - remaining] = value.code.toByte()
        }
        stringified = null
        remaining -= 1
        length++
        return this
    }

    actual override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        value ?: return this

        var current = startIndex
        while (current < endIndex) {
            val buffer = nonFullBuffer()
            val offset = CHAR_BUFFER_ARRAY_LENGTH - remaining
            val bytesToCopy = min(endIndex - current, remaining)

            buffer.withBufferLocked { array, _ ->
                for (i in 0 until bytesToCopy) {
                    array[offset + i] = value[current + i].code.toByte()
                }
            }

            current += bytesToCopy
            remaining -= bytesToCopy
        }

        stringified = null
        length += endIndex - startIndex
        return this
    }

    actual override fun append(value: CharSequence?): Appendable {
        value ?: return this
        return append(value, 0, value.length)
    }

    actual fun release() {
        val list = buffers

        if (list != null) {
            current = null
            for (i in 0 until list.size) {
                MutableDataPool.recycle(list[i])
            }
        } else {
            current?.let { MutableDataPool.recycle(it) }
            current = null
        }

        released.value = true
        buffers = null
        stringified = null
        length = 0
        remaining = 0
    }

    private fun copy(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex == endIndex) return ""

        val builder = StringBuilder(endIndex - startIndex)

        var buffer: MutableData

        var base = startIndex - (startIndex % CHAR_BUFFER_ARRAY_LENGTH)

        while (base < endIndex) {
            buffer = bufferForIndex(base)
            val innerStartIndex = maxOf(0, startIndex - base)
            val innerEndIndex = minOf(endIndex - base, CHAR_BUFFER_ARRAY_LENGTH)

            for (innerIndex in innerStartIndex until innerEndIndex) {
                builder.append(buffer[innerIndex].toInt().toChar())
            }

            base += CHAR_BUFFER_ARRAY_LENGTH
        }

        return builder
    }

    @Suppress("ConvertTwoComparisonsToRangeCheck")
    private inner class SubSequenceImpl(val start: Int, val end: Int) : CharSequence {
        private var stringified: String? by shared(null)

        override val length: Int
            get() = end - start

        override fun get(index: Int): Char {
            val withOffset = index + start
            require(index >= 0) { "index is negative: $index" }
            require(withOffset < end) { "index ($index) should be less than length ($length)" }

            return this@CharArrayBuilder.getImpl(withOffset)
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

            return rangeEqualsImpl(start, other, 0, length)
        }

        override fun hashCode() = stringified?.hashCode() ?: hashCodeImpl(start, end)
    }

    private fun bufferForIndex(index: Int): MutableData {
        val list = buffers

        if (list == null) {
            if (index >= CHAR_BUFFER_ARRAY_LENGTH) throwSingleBuffer(index)
            return current ?: throwSingleBuffer(index)
        }

        return list[index / CHAR_BUFFER_ARRAY_LENGTH]
    }

    private fun throwSingleBuffer(index: Int): Nothing {
        if (released.value) throw IllegalStateException("Buffer is already released")
        throw IndexOutOfBoundsException("$index is not in range [0; ${currentPosition()})")
    }

    private fun nonFullBuffer(): MutableData {
        return if (remaining == 0) appendNewArray() else current!!
    }

    private fun appendNewArray(): MutableData {
        val newBuffer = MutableDataPool.borrow()
        val existing = current
        current = newBuffer
        remaining = CHAR_BUFFER_ARRAY_LENGTH

        released.value = false

        if (existing != null) {
            val list = buffers ?: sharedList<MutableData>().also {
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
            hc = 31 * hc + getImpl(i).code
        }

        return hc
    }

    private fun currentPosition() = CHAR_BUFFER_ARRAY_LENGTH - remaining
}
