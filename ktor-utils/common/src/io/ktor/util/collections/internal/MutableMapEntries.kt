/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.util.collections.*
import io.ktor.utils.io.*

internal class MutableMapEntries<Key : Any, Value : Any>(
    private val delegate: ConcurrentMap<Key, Value>
) : MutableSet<MutableMap.MutableEntry<Key, Value>> {

    init {
        makeShared()
    }

    override fun add(element: MutableMap.MutableEntry<Key, Value>): Boolean =
        delegate.put(element.key, element.value) != element.value

    override fun addAll(elements: Collection<MutableMap.MutableEntry<Key, Value>>): Boolean {
        var result = false

        elements.forEach {
            result = add(it) || result
        }

        return result
    }

    override fun clear() {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<Key, Value>> =
        object : MutableIterator<MutableMap.MutableEntry<Key, Value>> {
            private val origin = delegate.iterator()
            override fun hasNext(): Boolean = origin.hasNext()

            override fun next(): MutableMap.MutableEntry<Key, Value> = origin.next()

            override fun remove(): Unit = origin.remove()
        }

    override fun remove(element: MutableMap.MutableEntry<Key, Value>): Boolean =
        delegate.remove(element.key) != null

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<Key, Value>>): Boolean {
        var modified = false
        elements.forEach {
            modified = remove(it) || modified
        }

        return modified
    }

    override fun retainAll(elements: Collection<MutableMap.MutableEntry<Key, Value>>): Boolean {
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

    override fun contains(element: MutableMap.MutableEntry<Key, Value>): Boolean =
        delegate[element.key] == element.value

    override fun containsAll(elements: Collection<MutableMap.MutableEntry<Key, Value>>): Boolean =
        elements.all { contains(it) }

    override fun isEmpty(): Boolean = delegate.isEmpty()
}
