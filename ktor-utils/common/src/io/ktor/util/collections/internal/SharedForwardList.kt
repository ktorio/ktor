/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*

internal class SharedForwardList<T : Any> : MutableIterable<T> {
    internal var head by shared<ForwardListNode<T>>(ForwardListNode(null, null, null))
    internal var tail by shared(head)

    init {
        makeShared()
    }

    fun appendFirst(value: T): ForwardListNode<T> {
        return head.insertAfter(value)
    }

    fun appendLast(value: T): ForwardListNode<T> {
        tail = tail.insertAfter(value)
        return tail
    }

    override fun iterator(): MutableIterator<T> =
        ForwardListIterator(head)
}

private class ForwardListIterator<T>(head: ForwardListNode<T>) : MutableIterator<T> {
    var previous by shared<ForwardListNode<T>?>(head)
    val current: ForwardListNode<T>? get() = previous?.next

    override fun hasNext(): Boolean = current?.item != null

    override fun next(): T {
        previous = current
        return previous?.item!!
    }

    override fun remove() {
        previous?.remove() ?: error("Fail to remove element before iteration")
    }
}

