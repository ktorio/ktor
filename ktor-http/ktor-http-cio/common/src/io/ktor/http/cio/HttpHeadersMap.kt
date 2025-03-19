/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.utils.io.pool.*
import kotlin.math.absoluteValue

private const val EXPECTED_HEADERS_QTY = 128

/*
 * index array structure
 * [0] name hash
 * [1] name start index
 * [2] name end (excl) index
 * [3] value start index
 * [4] value end (excl) index
 * [5] index of the next header with the same name (not only the same hash)
 */
private const val HEADER_SIZE = 6
private const val HEADER_ARRAY_POOL_SIZE = 1000
private const val HEADER_ARRAY_SIZE = EXPECTED_HEADERS_QTY * HEADER_SIZE
private const val EMPTY_INDEX = -1
private const val RESIZE_THRESHOLD = 0.75

private const val OFFSET_NAME_HASH = 0
private const val OFFSET_HEADER_NAME_START = 1
private const val OFFSET_HEADER_NAME_END = 2
private const val OFFSET_HEADER_VALUE_START = 3
private const val OFFSET_HEADER_VALUE_END = 4
private const val OFFSET_NEXT_HEADER = 5

/**
 * A headers map data structure used in CIO
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.HttpHeadersMap)
 */
public class HttpHeadersMap internal constructor(private val builder: CharArrayBuilder) {
    public var size: Int = 0
        private set

    private var headerCapacity: Int = 0
    private var headersData = HeadersDataPool.borrow()

    private fun thresholdReached(): Boolean = size >= headerCapacity * RESIZE_THRESHOLD

    @Deprecated("Use getAll instead", ReplaceWith("getAll(name)"))
    public fun find(name: String, fromIndex: Int = 0): Int {
        if (size == 0) return -1

        var offset = idxToOffset(fromIndex)
        var nextIndex = fromIndex

        while (headersData.at(offset + OFFSET_NAME_HASH) != EMPTY_INDEX) {
            if (headerHasName(name, offset)) {
                return nextIndex
            }
            nextIndex++
            offset = (offset / HEADER_SIZE) % headerCapacity
        }
        return -1
    }

    public operator fun get(name: String): CharSequence? {
        if (size == 0) return null

        val hash = name.hashCodeLowerCase().absoluteValue
        var headerIndex = hash % headerCapacity

        while (headersData.at(headerIndex * HEADER_SIZE + OFFSET_NAME_HASH) != EMPTY_INDEX) {
            if (headerHasName(name, headerIndex * HEADER_SIZE)) {
                return valueAtOffset(headerIndex * HEADER_SIZE)
            }
            headerIndex = (headerIndex + 1) % headerCapacity
        }

        return null
    }

    public fun getAll(name: String): Sequence<CharSequence> = sequence {
        if (size == 0) return@sequence

        val hash = name.hashCodeLowerCase().absoluteValue
        var headerIndex = hash % headerCapacity

        while (headersData.at(headerIndex * HEADER_SIZE + OFFSET_NAME_HASH) != EMPTY_INDEX) {
            if (headerHasName(name, headerIndex * HEADER_SIZE)) {
                yield(valueAtOffset(headerIndex * HEADER_SIZE))

                val nextHeaderIndex = headersData.at(headerIndex * HEADER_SIZE + OFFSET_NEXT_HEADER)
                if (nextHeaderIndex == EMPTY_INDEX) break
                headerIndex = nextHeaderIndex
                continue
            }
            headerIndex = (headerIndex + 1) % headerCapacity
        }
    }

    public fun offsets(): Sequence<Int> = headersData.headersStarts()

    @Deprecated(
        "Use put without `nameHash` and `valueHash` instead",
        ReplaceWith("put(nameStartIndex, nameEndIndex, valueStartIndex, valueEndIndex)")
    )
    public fun put(
        nameHash: Int,
        valueHash: Int,
        nameStartIndex: Int,
        nameEndIndex: Int,
        valueStartIndex: Int,
        valueEndIndex: Int
    ) {
        put(nameStartIndex, nameEndIndex, valueStartIndex, valueEndIndex)
    }

    public fun put(
        nameStartIndex: Int,
        nameEndIndex: Int,
        valueStartIndex: Int,
        valueEndIndex: Int
    ) {
        if (thresholdReached()) {
            resize()
        }

        val hash = builder.hashCodeLowerCase(nameStartIndex, nameEndIndex).absoluteValue
        val name = builder.subSequence(nameStartIndex, nameEndIndex)

        var headerIndex = hash % headerCapacity
        var sameNameHeaderIndex = EMPTY_INDEX
        while (headersData.at(headerIndex * HEADER_SIZE + OFFSET_NAME_HASH) != EMPTY_INDEX) {
            if (headerHasName(name, headerIndex * HEADER_SIZE)) {
                sameNameHeaderIndex = headerIndex
            }
            headerIndex = (headerIndex + 1) % headerCapacity
        }

        val headerOffset = headerIndex * HEADER_SIZE
        headersData.set(headerOffset + OFFSET_NAME_HASH, hash)
        headersData.set(headerOffset + OFFSET_HEADER_NAME_START, nameStartIndex)
        headersData.set(headerOffset + OFFSET_HEADER_NAME_END, nameEndIndex)
        headersData.set(headerOffset + OFFSET_HEADER_VALUE_START, valueStartIndex)
        headersData.set(headerOffset + OFFSET_HEADER_VALUE_END, valueEndIndex)
        headersData.set(headerOffset + OFFSET_NEXT_HEADER, EMPTY_INDEX)

        if (sameNameHeaderIndex != EMPTY_INDEX) {
            headersData.set(sameNameHeaderIndex * HEADER_SIZE + OFFSET_NEXT_HEADER, headerIndex)
        }

        size++
    }

    private fun idxToOffset(idx: Int): Int {
        require(idx >= 0)
        require(idx < size)
        return offsets().take(idx + 1).last()
    }

    @Deprecated("Use nameAtOffset instead", ReplaceWith("nameAtOffset"))
    public fun nameAt(idx: Int): CharSequence {
        return nameAtOffset(idxToOffset(idx))
    }

    @Deprecated("Use valueAtOffset instead", ReplaceWith("valueAtOffset"))
    public fun valueAt(idx: Int): CharSequence {
        return valueAtOffset(idxToOffset(idx))
    }

    private fun resize() {
        val prevSize = size
        val prevData = headersData

        size = 0
        headerCapacity = (headerCapacity * 2).or(EXPECTED_HEADERS_QTY)
        headersData = HeadersDataPool.borrow().apply { prepare((prevData.arraysCount() * 2).or(1)) }

        for (headerOffset in prevData.headersStarts()) {
            put(
                prevData.at(headerOffset + OFFSET_HEADER_NAME_START),
                prevData.at(headerOffset + OFFSET_HEADER_NAME_END),
                prevData.at(headerOffset + OFFSET_HEADER_VALUE_START),
                prevData.at(headerOffset + OFFSET_HEADER_VALUE_END)
            )
        }

        HeadersDataPool.recycle(prevData)
        require(prevSize == size)
    }

    private fun headerHasName(name: CharSequence, headerOffset: Int): Boolean {
        val nameStartIndex = headersData.at(headerOffset + OFFSET_HEADER_NAME_START)
        val nameEndIndex = headersData.at(headerOffset + OFFSET_HEADER_NAME_END)
        return builder.equalsLowerCase(nameStartIndex, nameEndIndex, name)
    }

    public fun nameAtOffset(headerOffset: Int): CharSequence {
        val nameStartIndex = headersData.at(headerOffset + OFFSET_HEADER_NAME_START)
        val nameEndIndex = headersData.at(headerOffset + OFFSET_HEADER_NAME_END)
        return builder.subSequence(nameStartIndex, nameEndIndex)
    }

    public fun valueAtOffset(headerOffset: Int): CharSequence {
        val valueStartIndex = headersData.at(headerOffset + OFFSET_HEADER_VALUE_START)
        val valueEndIndex = headersData.at(headerOffset + OFFSET_HEADER_VALUE_END)
        return builder.subSequence(valueStartIndex, valueEndIndex)
    }

    public fun release() {
        size = 0
        headerCapacity = 0
        HeadersDataPool.recycle(headersData)
        headersData = HeadersDataPool.borrow()
    }

    override fun toString(): String {
        return buildString { dumpTo("", this) }
    }
}

/**
 * Dump header values to [out], useful for debugging
 */
internal fun HttpHeadersMap.dumpTo(indent: String, out: Appendable) {
    for (offset in offsets()) {
        out.append(indent)
        out.append(nameAtOffset(offset))
        out.append(" => ")
        out.append(valueAtOffset(offset))
        out.append("\n")
    }
}

/**
 * Helper class that joins multiple IntArrays from IntArrayPool
 * */
private class HeadersData {

    private var arrays = mutableListOf<IntArray>()

    fun arraysCount(): Int = arrays.size

    fun prepare(subArraysCount: Int) {
        repeat(subArraysCount) {
            arrays.add(IntArrayPool.borrow())
        }
    }

    fun at(index: Int): Int {
        return arrays[index / HEADER_ARRAY_SIZE][index % HEADER_ARRAY_SIZE]
    }

    fun set(index: Int, value: Int) {
        arrays[index / HEADER_ARRAY_SIZE][index % HEADER_ARRAY_SIZE] = value
    }

    fun headersStarts(): Sequence<Int> = sequence {
        var joinedIndex = 0
        for (arr in arrays) {
            var localIndex = 0
            while (localIndex < arr.size) {
                if (at(joinedIndex + OFFSET_NAME_HASH) != EMPTY_INDEX) {
                    yield(joinedIndex)
                }
                localIndex += HEADER_SIZE
                joinedIndex += HEADER_SIZE
            }
        }
    }

    fun release() {
        for (array in arrays) IntArrayPool.recycle(array)
        arrays.clear()
    }
}

private val IntArrayPool: DefaultPool<IntArray> = object : DefaultPool<IntArray>(HEADER_ARRAY_POOL_SIZE) {
    override fun produceInstance(): IntArray = IntArray(HEADER_ARRAY_SIZE) { EMPTY_INDEX }

    override fun clearInstance(instance: IntArray): IntArray {
        instance.fill(EMPTY_INDEX)
        return super.clearInstance(instance)
    }
}

private val HeadersDataPool: DefaultPool<HeadersData> = object : DefaultPool<HeadersData>(HEADER_ARRAY_POOL_SIZE) {
    override fun produceInstance(): HeadersData = HeadersData()

    override fun clearInstance(instance: HeadersData): HeadersData {
        instance.release()
        return super.clearInstance(instance)
    }
}
