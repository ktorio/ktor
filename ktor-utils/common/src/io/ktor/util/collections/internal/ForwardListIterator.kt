/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.utils.io.concurrent.*

internal class ForwardListIterator<T : Any>(head: ForwardListNode<T>) : MutableIterator<T> {
    var previous by shared<ForwardListNode<T>?>(head)
    val current: ForwardListNode<T>? get() = previous?.next

    override fun hasNext(): Boolean = current?.item != null

    override fun next(): T {
        previous = current
        return previous?.item ?: throw NoSuchElementException()
    }

    override fun remove() {
        previous?.remove() ?: error("Fail to remove element before iteration")
    }
}
