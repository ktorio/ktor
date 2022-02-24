/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

@InternalAPI
internal class SharedList<T>(override val size: Int) : List<T?> {
    private val data: AtomicArray<T?> = atomicArrayOfNulls<T>(size)

    init {
        makeShared()
    }

    operator fun set(index: Int, value: T?) {
        data[index].value = value
    }

    override fun contains(element: T?): Boolean {
        for (index in 0 until size) {
            if (data[index].value == element) {
                return true
            }
        }

        return false
    }

    override fun containsAll(elements: Collection<T?>): Boolean = elements.all { contains(it) }

    override fun get(index: Int): T? = data[index].value

    override fun indexOf(element: T?): Int {
        for (index in 0 until size) {
            if (data[index].value == element) {
                return index
            }
        }

        return -1
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<T?> = listIterator(0)

    override fun lastIndexOf(element: T?): Int = asReversed().indexOf(element)

    override fun listIterator(): ListIterator<T?> = listIterator(0)

    override fun listIterator(index: Int): ListIterator<T?> = object : ListIterator<T?> {
        private val currentIndex = atomic(index)

        init {
            makeShared()
        }

        override fun hasNext(): Boolean = currentIndex.value < size

        override fun hasPrevious(): Boolean = currentIndex.value > 0

        override fun next(): T? {
            check(hasNext())
            val current = currentIndex.getAndIncrement()
            return data[current].value
        }

        override fun nextIndex(): Int {
            check(hasNext())
            return currentIndex.value + 1
        }

        override fun previous(): T? {
            check(hasPrevious())
            val current = currentIndex.getAndDecrement()
            return data[current].value
        }

        override fun previousIndex(): Int {
            check(hasPrevious())
            return currentIndex.value - 1
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T?> {
        val result = SharedList<T>(toIndex - fromIndex)

        for (index in fromIndex until toIndex) {
            result[index - fromIndex] = data[index].value
        }

        return result
    }
}
