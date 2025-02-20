/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import java.util.concurrent.*

/**
 * Ktor concurrent map implementation. Please do not use it.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.ConcurrentMap)
 */
public actual class ConcurrentMap<Key, Value> public actual constructor(initialCapacity: Int) : MutableMap<Key, Value> {
    private val delegate = ConcurrentHashMap<Key, Value>(initialCapacity)

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.ConcurrentMap.computeIfAbsent)
     */
    public actual fun computeIfAbsent(key: Key, block: () -> Value): Value = delegate.computeIfAbsent(key) {
        block()
    }

    actual override val size: Int
        get() = delegate.size

    actual override fun containsKey(key: Key): Boolean = delegate.containsKey(key)

    actual override fun containsValue(value: Value): Boolean = delegate.containsValue(value)

    actual override fun get(key: Key): Value? = delegate[key]

    actual override fun isEmpty(): Boolean = delegate.isEmpty()

    actual override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
        get() = delegate.entries

    actual override val keys: MutableSet<Key>
        get() = delegate.keys

    actual override val values: MutableCollection<Value>
        get() = delegate.values

    actual override fun clear() {
        delegate.clear()
    }

    actual override fun put(key: Key, value: Value): Value? = delegate.put(key, value)

    actual override fun putAll(from: Map<out Key, Value>) {
        delegate.putAll(from)
    }

    actual override fun remove(key: Key): Value? = delegate.remove(key)

    actual override fun remove(key: Key, value: Value): Boolean = delegate.remove(key, value)

    override fun hashCode(): Int = delegate.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is Map<*, *>) return false
        return other == delegate
    }

    override fun toString(): String = "ConcurrentMapJvm by $delegate"
}
