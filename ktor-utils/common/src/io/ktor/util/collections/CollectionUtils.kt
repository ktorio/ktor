/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*

public fun <T> sharedListOf(vararg values: T): MutableList<T> {
    if (PlatformUtils.IS_NATIVE) {
        return ConcurrentList<T>().apply {
            addAll(values)
        }
    }

    return values.mapTo(ArrayList(values.size)) { it }
}

public fun <K : Any, V : Any> sharedMap(initialCapacity: Int = 8): MutableMap<K, V> {
    if (PlatformUtils.IS_NATIVE) {
        return ConcurrentMap(initialCapacity = initialCapacity)
    }

    return mutableMapOf()
}

public fun <V> sharedList(): MutableList<V> {
    if (PlatformUtils.IS_NATIVE) {
        return ConcurrentList()
    }

    return mutableListOf()
}
