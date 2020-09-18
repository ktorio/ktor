/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*

internal class ForwardListNode<T>(
    next: ForwardListNode<T>?,
    val item: T?,
    previous: ForwardListNode<T>?
) {
    var next by shared(next)
    var previous: ForwardListNode<T>? by shared(previous)

    init {
        makeShared()
    }

    fun insertAfter(value: T): ForwardListNode<T> {
        val result = ForwardListNode(next, value, this)
        next = result
        return result
    }

    fun removeNext() {
        next = next?.next
        next?.previous = this
    }

    fun remove() {
        previous!!.removeNext()
    }
}
