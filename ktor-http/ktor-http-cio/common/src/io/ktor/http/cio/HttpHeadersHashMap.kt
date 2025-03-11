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
private const val RESIZE_THRESHOLD = 0.7

private const val OFFSET_NAME_HASH = 0
private const val OFFSET_HEADER_NAME_START = 1
private const val OFFSET_HEADER_NAME_END = 2
private const val OFFSET_HEADER_VALUE_START = 3
private const val OFFSET_HEADER_VALUE_END = 4
private const val OFFSET_NEXT_HEADER = 5

/**
 * Hash multimap data structure implemented using Open addressing and Linear probing.
 * This is an updated version of HttpHeadersMap that is known to have problems with hash collisions and search speed.
 */
public class HttpHeadersHashMap internal constructor(private val builder: CharArrayBuilder) {
    public var valuesCount: Int = 0 // number of values stored (values with the same key counted multiple times)
        private set

    private var headerCapacity: Int = 0
    private var headerThreshold: Int = 0
    private var headersData = HeadersData(0)

    /**
     * Returns all values that match `name` joined by comma or null if there was no matching header
     * */
    public operator fun get(name: String): String? {
        val all = getAll(name)
        return if (all.none()) null else all.joinToString(",")
    }

    public fun getAll(name: String): Sequence<CharSequence> = sequence {
        if (valuesCount == 0) return@sequence

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

    public fun put(
        nameStartIndex: Int,
        nameEndIndex: Int,
        valueStartIndex: Int,
        valueEndIndex: Int
    ) {
        if (valuesCount >= headerThreshold) {
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

        valuesCount++
    }

    private fun resize() {
        val prevValuesCount = valuesCount
        val prevData = headersData

        valuesCount = 0
        headerCapacity = (headerCapacity * 2).or(EXPECTED_HEADERS_QTY)
        headerThreshold = headerCapacity.times(RESIZE_THRESHOLD).toInt()
        headersData = HeadersData((headersData.subArraysCount * 2).or(1))

        for (headerOffset in prevData.headersStarts()) {
            put(
                prevData.at(headerOffset + OFFSET_HEADER_NAME_START),
                prevData.at(headerOffset + OFFSET_HEADER_NAME_END),
                prevData.at(headerOffset + OFFSET_HEADER_VALUE_START),
                prevData.at(headerOffset + OFFSET_HEADER_VALUE_END)
            )
        }

        prevData.release()
        require(prevValuesCount == valuesCount)
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
        valuesCount = 0
        headerCapacity = 0
        headerThreshold = 0
        headersData.release()
        headersData = HeadersData(0)
    }

    override fun toString(): String {
        return buildString { dumpTo("", this) }
    }
}

/**
 * Dump header values to [out], useful for debugging
 */
internal fun HttpHeadersHashMap.dumpTo(indent: String, out: Appendable) {
    for (offset in offsets()) {
        out.append(indent)
        out.append(nameAtOffset(offset))
        out.append(" => ")
        out.append(valueAtOffset(offset))
        out.append("\n")
    }
}

private val IntArrayPool: DefaultPool<IntArray> = object : DefaultPool<IntArray>(HEADER_ARRAY_POOL_SIZE) {
    override fun produceInstance(): IntArray = IntArray(HEADER_ARRAY_SIZE) { EMPTY_INDEX }

    override fun clearInstance(instance: IntArray): IntArray {
        instance.fill(EMPTY_INDEX)
        return super.clearInstance(instance)
    }
}

/**
 * Helper class that joins multiple IntArrays from IntArrayPool
 * */
private class HeadersData(val subArraysCount: Int) {

    private val arrays = MutableList(subArraysCount) { IntArrayPool.borrow() }

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
    }
}
