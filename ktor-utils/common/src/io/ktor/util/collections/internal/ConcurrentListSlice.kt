/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import kotlin.math.*

internal class ConcurrentListSlice<T>(
    private val origin: MutableList<T>,
    private val fromIndex: Int,
    private val toIndex: Int
) : AbstractMutableList<T>() {
    override val size: Int
        get() = min(origin.size, toIndex - fromIndex)

    override fun get(index: Int): T {
        return origin[fromIndex + index]
    }

    override fun add(index: Int, element: T) {
        error("Unsupported append in ConcurrentList slice")
    }

    override fun removeAt(index: Int): T {
        error("Unsupported remove in ConcurrentList slice")
    }

    override fun set(index: Int, element: T): T = origin.set(fromIndex + index, element)
}
