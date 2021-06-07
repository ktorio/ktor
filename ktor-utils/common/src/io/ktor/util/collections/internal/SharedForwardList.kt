/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*

internal class SharedForwardList<T : Any> : MutableIterable<T> {
    internal var head: ForwardListNode<T>? by shared(null)
    internal var tail by shared(head)

    init {
        makeShared()

        head = ForwardListNode(this, null, null, null)
        tail = head
    }

    fun first(): ForwardListNode<T>? {
        return head!!.next
    }

    fun last(): ForwardListNode<T>? {
        if (head == tail) {
            return null
        }

        return tail
    }

    fun appendFirst(value: T): ForwardListNode<T> {
        val newValue = head!!.insertAfter(value)
        if (head == tail) {
            tail = newValue
        }

        return newValue
    }

    fun appendLast(value: T): ForwardListNode<T> {
        tail = tail!!.insertAfter(value)
        return tail!!
    }

    override fun iterator(): MutableIterator<T> =
        ForwardListIterator(head!!)
}
