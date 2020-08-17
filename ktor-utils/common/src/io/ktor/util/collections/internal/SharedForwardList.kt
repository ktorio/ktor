/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*

internal class SharedForwardList<T : Any> : MutableIterable<T> {
    private val head: AtomicRef<ListItem<T>?> = atomic(null)

    init {
        makeShared()
    }

    fun appendHead(item: T) {
        while (true) {
            val value = head.value
            val update = ListItem(value, item)
            if (head.compareAndSet(value, update)) break
        }
    }

    override fun iterator(): MutableIterator<T> =
        ForwardListIterator(head.value)
}

private class ForwardListIterator<T>(value: ListItem<T>?) : MutableIterator<T> {
    var last by shared<ListItem<T>?>(null)
    var current by shared(value)

    init {
        skipRemoved()
    }

    override fun hasNext(): Boolean = current?.item != null

    override fun next(): T {
        last = current
        current = current?.next
        skipRemoved()

        return last?.item!!
    }

    override fun remove() {
        val lastReturned = last!!
        lastReturned.removed = true
    }

    private fun skipRemoved() {
        while (current != null && current?.removed == true) {
            current = current?.next
        }
    }
}

private class ListItem<T>(
    val next: ListItem<T>?,
    val item: T?
) {
    var removed by shared(false)

    init {
        makeShared()
    }
}
