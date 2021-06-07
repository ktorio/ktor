/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.util.collections.internal.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.locks.*

private const val INITIAL_CAPACITY = 32

public class ConcurrentList<T> : MutableList<T> {
    private var data by shared(SharedList<T>(INITIAL_CAPACITY))

    override var size: Int by shared(0)
        private set

    private val lock = SynchronizedObject()

    init {
        makeShared()
    }

    override fun hashCode(): Int = synchronized(lock) {
        return@synchronized fold(7) { state, current -> Hash.combine(state, current.hashCode()) }
    }

    override fun equals(other: Any?): Boolean = synchronized(lock) {
        if (other == null || other !is List<*> || other.size != size) {
            return@synchronized false
        }

        forEachIndexed { index, item ->
            if (other[index] != item) return@synchronized false
        }

        return@synchronized true
    }

    override fun toString(): String = synchronized(lock) {
        return@synchronized buildString {
            append('[')
            this@ConcurrentList.forEachIndexed { index, item ->
                append("$item")

                if (index + 1 < size) {
                    append(", ")
                }
            }

            append(']')
        }
    }

    override fun contains(element: T): Boolean = indexOf(element) >= 0

    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    override fun get(index: Int): T = synchronized(lock) {
        if (index >= size) {
            throw NoSuchElementException()
        }

        return data[index]!!
    }

    override fun indexOf(element: T): Int = synchronized(lock) {
        for (index in 0 until size) {
            if (data[index] == element) {
                return index
            }
        }

        return -1
    }

    override fun isEmpty(): Boolean = size == 0

    override fun lastIndexOf(element: T): Int = synchronized(lock) {
        for (index in size - 1 downTo 0) {
            if (data[index] == element) {
                return index
            }
        }

        return -1
    }

    override fun add(element: T): Boolean = synchronized(lock) {
        if (size >= data.size) {
            increaseCapacity()
        }

        data[size] = element
        size += 1
        return true
    }

    override fun add(index: Int, element: T) {
        reserve(index, 1)
        data[index] = element
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        reserve(index, elements.size)

        var current = index
        for (item in elements) {
            data[current] = item
            current += 1
        }

        return elements.isNotEmpty()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        elements.all { add(it) }
        return elements.isNotEmpty()
    }

    override fun clear(): Unit = synchronized(lock) {
        data = SharedList(INITIAL_CAPACITY)
        size = 0
    }

    override fun iterator(): MutableIterator<T> = listIterator()

    override fun listIterator(): MutableListIterator<T> = listIterator(0)

    override fun listIterator(index: Int): MutableListIterator<T> = object : MutableListIterator<T> {
        var current by shared(index)

        override fun hasNext(): Boolean = current < this@ConcurrentList.size

        override fun next(): T = this@ConcurrentList[current++]

        override fun remove() {
            removeAt(current - 1)
            current--
        }

        override fun hasPrevious(): Boolean = current > 0

        override fun nextIndex(): Int = current + 1

        override fun previous(): T = this@ConcurrentList[current--]

        override fun previousIndex(): Int = current - 1

        override fun add(element: T) {
            add(current, element)
        }

        override fun set(element: T) {
            this@ConcurrentList[current - 1] = element
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
        ConcurrentListSlice(this, fromIndex, toIndex)

    override fun remove(element: T): Boolean = synchronized(lock) {
        val index = indexOf(element)
        if (index < 0) {
            return false
        }

        removeAt(index)
        return true
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var result = false
        elements.forEach { result = remove(it) || result }
        return result
    }

    override fun removeAt(index: Int): T = synchronized(lock) {
        checkIndex(index)

        val old = data[index]
        data[index] = null

        sweep(index)
        return old!!
    }

    override fun retainAll(elements: Collection<T>): Boolean = synchronized(lock) {
        var changed = false
        var firstNull = -1
        for (index in 0 until size) {
            val item = data[index]!!

            if (item !in elements) {
                changed = true
                data[index] = null

                if (firstNull < 0) {
                    firstNull = index
                }
            }
        }

        if (changed) {
            sweep(firstNull)
        }

        return changed
    }

    override fun set(index: Int, element: T): T = synchronized(lock) {
        checkIndex(index)
        val old = data[index]
        data[index] = element

        return old ?: element
    }

    private fun checkIndex(index: Int) {
        if (index >= size || index < 0) throw IndexOutOfBoundsException()
    }

    private fun increaseCapacity(targetCapacity: Int = data.size * 2) {
        val newData = SharedList<T>(targetCapacity)
        for (index in 0 until data.size) {
            newData[index] = data[index]
        }

        data = newData
    }

    private fun sweep(firstNull: Int) {
        var writePosition = firstNull

        for (index in writePosition + 1 until size) {
            if (data[index] == null) {
                continue
            }

            data[writePosition] = data[index]
            writePosition += 1
        }

        for (index in writePosition until size) {
            data[index] = null
        }

        size = writePosition
    }

    private fun reserve(index: Int, gapSize: Int) {
        val targetSize = gapSize + size
        while (data.size < targetSize) {
            increaseCapacity()
        }

        var readPosition = size - 1
        while (readPosition >= index) {
            data[readPosition + gapSize] = data[readPosition]
            readPosition -= 1
        }

        for (current in index until index + gapSize) {
            data[current] = null
        }

        size += gapSize
    }
}
