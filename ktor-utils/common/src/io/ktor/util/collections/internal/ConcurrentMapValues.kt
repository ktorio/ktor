/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.util.collections.*
import io.ktor.utils.io.*

internal class ConcurrentMapValues<Key : Any, Value : Any>(
    private val delegate: ConcurrentMap<Key, Value>
) : MutableCollection<Value> {

    init {
        makeShared()
    }

    override val size: Int
        get() = delegate.size

    override fun contains(element: Value): Boolean = delegate.containsValue(element)

    override fun containsAll(elements: Collection<Value>): Boolean = elements.all { contains(it) }

    override fun isEmpty(): Boolean = delegate.size == 0

    override fun add(element: Value): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addAll(elements: Collection<Value>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<Value> = object : MutableIterator<Value> {
        val delegateIterator = delegate.iterator()

        init {
            makeShared()
        }

        override fun hasNext(): Boolean = delegateIterator.hasNext()

        override fun next(): Value = delegateIterator.next().value

        override fun remove() {
            delegateIterator.remove()
        }
    }

    override fun remove(element: Value): Boolean {
        var modified = false

        with(iterator()) {
            while (hasNext()) {
                if (next() == element) {
                    continue
                }

                modified = true
                remove()
            }
        }

        return modified
    }

    override fun removeAll(elements: Collection<Value>): Boolean {
        var modified = false

        with(iterator()) {
            while (hasNext()) {
                if (next() !in elements) {
                    continue
                }

                modified = true
                remove()
            }
        }

        return modified
    }

    override fun retainAll(elements: Collection<Value>): Boolean {
        error("Common concurrent map doesn't support this operation yet.")
    }
}
