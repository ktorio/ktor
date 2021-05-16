/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.*

internal fun <T> sharedListOf(vararg values: T): MutableList<T> {
    if (PlatformUtils.IS_NATIVE) {
        return ConcurrentList<T>().apply {
            addAll(values)
        }
    }

    return values.mapTo(ArrayList(values.size)) { it }
}

@InternalAPI
public fun <K : Any, V : Any> sharedMap(initialCapacity: Int): MutableMap<K, V> =
    if (PlatformUtils.IS_NATIVE) ConcurrentMap(initialCapacity = initialCapacity) else LinkedHashMap(initialCapacity)

@InternalAPI
public fun <K : Any, V : Any> sharedMap(): MutableMap<K, V> =
    if (PlatformUtils.IS_NATIVE) ConcurrentMap() else LinkedHashMap()

@InternalAPI
public fun <K : Any, V : Any> sharedMap(old: Map<K, V>): MutableMap<K, V> =
    if (PlatformUtils.IS_NATIVE) ConcurrentMap<K, V>().apply { putAll(old) } else LinkedHashMap(old)

@InternalAPI
public fun <T> sharedList(size: Int): MutableList<T> =
    if (PlatformUtils.IS_NATIVE) ConcurrentList() else ArrayList(size)

@InternalAPI
public fun <V> sharedList(): MutableList<V> =
    if (PlatformUtils.IS_NATIVE) ConcurrentList() else ArrayList()
