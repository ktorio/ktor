package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.io.pool.*


private const val EXPECTED_HEADERS_QTY = 32
/*
 * index array structure
 * [0] = name hash
 * [1] = value hash
 * [2] name start index
 * [3] name end (excl) index
 * [4] value start index
 * [5] value end (excl) index
 * [6] next entry index (multiplied) with the same name hash
 * [7] reserved
 */
private const val HEADER_SIZE = 8
private const val HEADER_ARRAY_POOL_SIZE = 1000

private val EMPTY_INT_ARRAY = IntArray(0)

class HttpHeadersMap internal constructor(private val builder: CharBufferBuilder) {
    var size = 0
        private set

    private var indexes = IntArrayPool.borrow()

    fun put(nameHash: Int, valueHash: Int, nameStartIndex: Int, nameEndIndex: Int, valueStartIndex: Int, valueEndIndex: Int) {
        val base = size * HEADER_SIZE
        val array = indexes

        if (base >= indexes.size) TODO("Implement headers overflow")

        array[base + 0] = nameHash
        array[base + 1] = valueHash
        array[base + 2] = nameStartIndex
        array[base + 3] = nameEndIndex
        array[base + 4] = valueStartIndex
        array[base + 5] = valueEndIndex
        array[base + 6] = -1  // TODO
        array[base + 7] = -1

        size++
    }

    operator fun get(name: String): CharSequence? {
        val nameHash = name.hashCodeLowerCase()
        for (i in 0 until size) {
            val offset = i * HEADER_SIZE
            if (indexes[offset] == nameHash) {
                return builder.subSequence(indexes[offset + 4], indexes[offset + 5])
            }
        }

        return null
    }

    fun getAll(name: String): Sequence<CharSequence> {
        val nameHash = name.hashCodeLowerCase()
        return generateSequence(0) { if (it + 1 >= size) null else it + 1 }
                .map { it * HEADER_SIZE }
                .filter { indexes[it] == nameHash }
                .map { builder.subSequence(indexes[it + 4], indexes[it + 5]) }
    }

    fun nameAt(idx: Int): CharSequence {
        require(idx >= 0)
        require(idx < size)

        val offset = idx * HEADER_SIZE
        val array = indexes

        val nameStart = array[offset + 2]
        val nameEnd = array[offset + 3]

        return builder.subSequence(nameStart, nameEnd)
    }

    fun valueAt(idx: Int): CharSequence {
        require(idx >= 0)
        require(idx < size)

        val offset = idx * HEADER_SIZE
        val array = indexes

        val nameStart = array[offset + 4]
        val nameEnd = array[offset + 5]

        return builder.subSequence(nameStart, nameEnd)
    }

    fun release() {
        size = 0
        val indexes = indexes
        this.indexes = EMPTY_INT_ARRAY

        if (indexes !== EMPTY_INT_ARRAY) IntArrayPool.recycle(indexes)
    }
}

fun HttpHeadersMap.dumpTo(indent: String, out: Appendable) {
    for (i in 0 until size) {
        out.append(indent)
        out.append(nameAt(i))
        out.append(" => ")
        out.append(valueAt(i))
        out.append("\n")
    }
}

private val IntArrayPool = object : DefaultPool<IntArray>(HEADER_ARRAY_POOL_SIZE) {
    override fun produceInstance(): IntArray = IntArray(EXPECTED_HEADERS_QTY * HEADER_SIZE)
}