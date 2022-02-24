/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.util.collections.*
import io.ktor.utils.io.*

internal class ConcurrentMapKeys<Key : Any, Value : Any>(
    private val delegate: ConcurrentMap<Key, Value>
) : MutableSet<Key> {

    init {
        makeShared()
    }

    override fun add(element: Key): Boolean = throw UnsupportedOperationException()

    override fun addAll(elements: Collection<Key>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<Key> = object : MutableIterator<Key> {
        private val delegateIterator: MutableIterator<MutableMap.MutableEntry<Key, Value>> = delegate.iterator()

        init {
            makeShared()
        }

        override fun hasNext(): Boolean = delegateIterator.hasNext()

        override fun next(): Key = delegateIterator.next().key

        override fun remove() {
            delegateIterator.remove()
        }
    }

    override fun remove(element: Key): Boolean = delegate.remove(element) != null

    override fun removeAll(elements: Collection<Key>): Boolean {
        var modified = false
        elements.forEach {
            modified = remove(it) || modified
        }

        return modified
    }

    override fun retainAll(elements: Collection<Key>): Boolean {
        var modified = false

        with(iterator()) {
            while (hasNext()) {
                if (next() in elements) {
                    continue
                }

                modified = true
                remove()
            }
        }

        return modified
    }

    override val size: Int
        get() = delegate.size

    override fun contains(element: Key): Boolean = delegate.contains(element)

    override fun containsAll(elements: Collection<Key>): Boolean = elements.all { contains(it) }

    override fun isEmpty(): Boolean = size == 0
}
