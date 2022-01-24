/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

internal const val INITIAL_CAPACITY = 32

/**
 * Ktor concurrent map implementation. Please do not use it.
 */
public expect class ConcurrentMap<Key, Value>(
    initialCapacity: Int = INITIAL_CAPACITY
) : MutableMap<Key, Value> {

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     */
    public fun computeIfAbsent(key: Key, block: () -> Value): Value
}
