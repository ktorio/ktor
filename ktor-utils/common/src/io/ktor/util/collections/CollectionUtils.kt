/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableListOf"))
public fun <T> sharedListOf(vararg values: T): MutableList<T> {
    if (PlatformUtils.IS_NATIVE && !PlatformUtils.IS_NEW_MM_ENABLED) {
        return ConcurrentList<T>().apply {
            addAll(values)
        }
    }

    return values.mapTo(ArrayList(values.size)) { it }
}

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableMapOf"))
public fun <K : Any, V : Any> sharedMap(initialCapacity: Int = 8): MutableMap<K, V> {
    if (PlatformUtils.IS_NATIVE && !PlatformUtils.IS_NEW_MM_ENABLED) {
        return ConcurrentMap(initialCapacity = initialCapacity)
    }

    return mutableMapOf()
}

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableListOf<V>()"))
public fun <V> sharedList(): MutableList<V> {
    if (PlatformUtils.IS_NATIVE && !PlatformUtils.IS_NEW_MM_ENABLED) {
        return ConcurrentList()
    }

    return mutableListOf()
}
