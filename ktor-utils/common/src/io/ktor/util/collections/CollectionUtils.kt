/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableListOf(values)"))
public fun <T> sharedListOf(vararg values: T): MutableList<T> = mutableListOf(*values)

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableMapOf()"))
public fun <K : Any, V : Any> sharedMap(initialCapacity: Int = 8): MutableMap<K, V> = LinkedHashMap(initialCapacity)

@Deprecated("Will be dropped with new memory model enabled by default", ReplaceWith("mutableListOf<V>()"))
public fun <V> sharedList(): MutableList<V> = mutableListOf()
