// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import kotlinx.atomicfu.locks.*

/**
 * Ktor concurrent map implementation. Please do not use it.
 */
public actual class ConcurrentMap<Key, Value> public actual constructor(
    initialCapacity: Int
) : MutableMap<Key, Value> {
    private val delegate = LinkedHashMap<Key, Value>(initialCapacity)
    private val lock = SynchronizedObject()

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     */
    public actual fun computeIfAbsent(key: Key, block: () -> Value): Value = synchronized(lock) {
        if (delegate.containsKey(key)) return delegate[key]!!
        val value = block()
        delegate[key] = value
        return value
    }

    override val size: Int
        get() = delegate.size

    override fun containsKey(key: Key): Boolean = synchronized(lock) { delegate.containsKey(key) }

    override fun containsValue(value: Value): Boolean = synchronized(lock) { delegate.containsValue(value) }

    override fun get(key: Key): Value? = synchronized(lock) { delegate[key] }

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
        get() = delegate.entries

    override val keys: MutableSet<Key>
        get() = delegate.keys

    override val values: MutableCollection<Value>
        get() = delegate.values

    override fun clear() {
        synchronized(lock) {
            delegate.clear()
        }
    }

    override fun put(key: Key, value: Value): Value? = synchronized(lock) { delegate.put(key, value) }

    override fun putAll(from: Map<out Key, Value>) {
        synchronized(lock) {
            delegate.putAll(from)
        }
    }

    override fun remove(key: Key): Value? = synchronized(lock) { delegate.remove(key) }

    override fun hashCode(): Int = synchronized(lock) { delegate.hashCode() }

    override fun equals(other: Any?): Boolean = synchronized(lock) {
        if (other !is Map<*, *>) return false
        return other == delegate
    }

    override fun toString(): String = "ConcurrentMapJs by $delegate"
}
