/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.utils.io.core.*

@InternalAPI
open class ConcurrentCollection<E>(
    private val delegate: MutableCollection<E>,
    private val lock: Lock
) : MutableCollection<E> {

    override val size: Int get() = lock.use {
        delegate.size
    }

    override fun contains(element: E): Boolean = lock.use {
        delegate.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean = lock.use {
        delegate.containsAll(elements)
    }

    override fun isEmpty(): Boolean = lock.use {
        delegate.isEmpty()
    }

    override fun add(element: E): Boolean = lock.use {
        delegate.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean = lock.use {
        delegate.addAll(elements)
    }

    override fun clear() = lock.use {
        delegate.clear()
    }

    override fun iterator(): MutableIterator<E> = delegate.iterator()

    override fun remove(element: E): Boolean = lock.use {
        delegate.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean = lock.use {
        delegate.removeAll(elements)
    }

    override fun retainAll(elements: Collection<E>): Boolean = lock.use {
        delegate.retainAll(elements)
    }
}
