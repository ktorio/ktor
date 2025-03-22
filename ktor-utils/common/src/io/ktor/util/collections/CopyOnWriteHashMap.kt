/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*

/**
 * This is an internal implementation for copy-on-write concurrent map.
 * It is very limited since it is not intended as general purpose implementation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap)
 */
@InternalAPI
public class CopyOnWriteHashMap<K : Any, V : Any> {
    private val current = atomic(emptyMap<K, V>())

    /**
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap.put)
     *
     * @see MutableMap.put
     */
    public fun put(key: K, value: V): V? {
        do {
            val old = current.value
            if (old[key] === value) return value

            val copy = HashMap(old)
            val replaced = copy.put(key, value)
            if (current.compareAndSet(old, copy)) return replaced
        } while (true)
    }

    /**
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap.get)
     *
     * @see Map.get
     */
    public operator fun get(key: K): V? = current.value[key]

    /**
     * Operator function for array access syntax
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap.set)
     */
    public operator fun set(key: K, value: V) {
        put(key, value)
    }

    /**
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap.remove)
     *
     * @see MutableMap.remove
     */
    public fun remove(key: K): V? {
        do {
            val old = current.value
            if (old[key] == null) return null

            val copy = HashMap(old)
            val removed = copy.remove(key)
            if (current.compareAndSet(old, copy)) return removed
        } while (true)
    }

    /**
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.CopyOnWriteHashMap.computeIfAbsent)
     *
     * @see MutableMap.computeIfAbsent
     */
    public fun computeIfAbsent(key: K, producer: (key: K) -> V): V {
        do {
            val old = current.value
            old[key]?.let { return it }

            val copy = HashMap(old)
            val newValue = producer(key)
            copy[key] = newValue
            if (current.compareAndSet(old, copy)) return newValue
        } while (true)
    }
}
