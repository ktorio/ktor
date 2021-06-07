/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Concurrent set implemented on top of [ConcurrentMap]
 */
@InternalAPI
public class ConcurrentSet<Key : Any> constructor(
    private val lock: Lock = Lock(),
    private val delegate: ConcurrentMap<Key, Unit> = ConcurrentMap(lock)
) : MutableSet<Key> {
    init {
        makeShared()
    }

    override fun add(element: Key): Boolean = lock.withLock {
        val result = !delegate.contains(element)
        delegate[element] = Unit
        return@withLock result
    }

    override fun addAll(elements: Collection<Key>): Boolean {
        var result = false
        for (item in elements) {
            result = add(item) || result
        }

        return result
    }

    override fun clear() {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<Key> = object : MutableIterator<Key> {
        val iterator = delegate.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): Key = iterator.next().key

        override fun remove() {
            iterator.remove()
        }
    }

    override fun remove(element: Key): Boolean = delegate.remove(element) == Unit

    override fun removeAll(elements: Collection<Key>): Boolean = elements.all { remove(it) }

    override fun retainAll(elements: Collection<Key>): Boolean {
        var modified = false

        with(iterator()) {
            while (hasNext()) {
                if (next() in elements) {
                    continue
                }

                remove()
                modified = true
            }
        }

        return modified
    }

    override val size: Int
        get() = delegate.size

    override fun contains(element: Key): Boolean = delegate.containsKey(element)

    override fun containsAll(elements: Collection<Key>): Boolean = elements.all { delegate.contains(it) }

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun toString(): String = lock.withLock {
        return@withLock buildString {
            append("[")

            this@ConcurrentSet.forEachIndexed { index, item ->
                append("$item")
                if (index != size - 1) {
                    append(", ")
                }
            }

            append("]")
        }
    }

    override fun equals(other: Any?): Boolean = lock.withLock {
        if (other == null || other !is Set<*> || other.size != size) {
            return@withLock false
        }

        for (item in this) {
            if (!other.contains(item)) {
                return@withLock false
            }
        }

        return@withLock true
    }

    override fun hashCode(): Int = lock.withLock {
        var result = 7
        forEach {
            result = Hash.combine(it.hashCode(), result)
        }

        return@withLock result
    }
}
