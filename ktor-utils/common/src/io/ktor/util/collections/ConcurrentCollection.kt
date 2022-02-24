/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
public open class ConcurrentCollection<E>(
    private val delegate: MutableCollection<E>,
    private val lock: Lock
) : MutableCollection<E> {

    override val size: Int get() = lock.withLock {
        delegate.size
    }

    override fun contains(element: E): Boolean = lock.withLock {
        delegate.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean = lock.withLock {
        delegate.containsAll(elements)
    }

    override fun isEmpty(): Boolean = lock.withLock {
        delegate.isEmpty()
    }

    override fun add(element: E): Boolean = lock.withLock {
        delegate.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean = lock.withLock {
        delegate.addAll(elements)
    }

    override fun clear(): Unit = lock.withLock {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<E> = delegate.iterator()

    override fun remove(element: E): Boolean = lock.withLock {
        delegate.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean = lock.withLock {
        delegate.removeAll(elements)
    }

    override fun retainAll(elements: Collection<E>): Boolean = lock.withLock {
        delegate.retainAll(elements)
    }
}
