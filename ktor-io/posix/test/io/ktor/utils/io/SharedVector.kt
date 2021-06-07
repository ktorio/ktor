/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*

internal class SharedVector<T> {
    private var _size by shared(0)
    private var content: AtomicArray<T?> by shared(atomicArrayOfNulls<T>(10))

    val size: Int get() = _size

    fun push(element: T) {
        if (_size >= content.size) {
            increaseCapacity()
        }

        content[_size].value = element
        _size++
    }

    operator fun get(index: Int): T {
        if (index >= _size) {
            throw IndexOutOfBoundsException("Index: $index, size: $size.")
        }

        return content[index].value!!
    }

    operator fun set(index: Int, element: T) {
        if (index >= _size) {
            throw IndexOutOfBoundsException("Index: $index, size: $size.")
        }

        content[index].value = element
    }

    fun find(element: T): Int {
        for (index in 0 until _size) {
            if (element == content[index].value) {
                return index
            }
        }

        return -1
    }

    fun remove(element: T): Boolean {
        val index = find(element)
        if (index < 0) {
            return false
        }

        removeAt(index)
        return true
    }

    fun removeAt(index: Int) {
        if (index >= size) {
            throw IndexOutOfBoundsException("Index: $index, size: $size.")
        }

        for (current in index until _size - 1) {
            content[current].value = content[current + 1].value
        }

        dropLast()
    }

    fun dropLast() {
        content[_size - 1].value = null
        _size--
    }

    fun clear() {
        _size = 0
        content = atomicArrayOfNulls(0)
    }

    private fun increaseCapacity() {
        val newContent = atomicArrayOfNulls<T>(content.size * 2)
        for (index in 0 until content.size) {
            newContent[index].value = content[index].value
        }

        content = newContent
    }
}
