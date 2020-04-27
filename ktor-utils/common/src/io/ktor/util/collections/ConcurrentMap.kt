/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.utils.io.core.*

@InternalAPI
class ConcurrentMap<Key, Value>(
    private val lock: Lock = Lock(),
    private val delegate: MutableMap<Key, Value> = mutableMapOf<Key, Value>()
) : MutableMap<Key, Value> {

    override val size: Int get() = lock.withLock { delegate.size }

    override fun containsKey(key: Key): Boolean = lock.withLock {
        delegate.containsKey(key)
    }

    override fun containsValue(value: Value): Boolean = lock.withLock {
        delegate.containsValue(value)
    }

    override fun get(key: Key): Value? = lock.withLock {
        delegate[key]
    }

    override fun isEmpty(): Boolean = lock.withLock {
        delegate.isEmpty()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>> = ConcurrentSet(delegate.entries, lock)

    override val keys: MutableSet<Key> = ConcurrentSet(delegate.keys, lock)

    override val values: MutableCollection<Value> = ConcurrentCollection(delegate.values, lock)

    override fun clear() = lock.withLock {
        delegate.clear()
    }

    override fun put(key: Key, value: Value): Value? = lock.withLock {
        delegate.put(key, value)
    }

    override fun putAll(from: Map<out Key, Value>) = lock.withLock {
        delegate.putAll(from)
    }

    override fun remove(key: Key): Value? = lock.withLock {
        delegate.remove(key)
    }

    /**
     * Perform concurrent insert.
     */
    @Deprecated(
        "This is accidentally does insert instead of get. Use computeIfAbsent or getOrElse instead.",
        level = DeprecationLevel.ERROR
    )
    fun getOrDefault(key: Key, block: () -> Value): Value = lock.withLock {
        return computeIfAbsent(key, block)
    }

    /**
     * Perform concurrent get and compute [block] if no associated value found in the map and insert the new value.
     * @return an existing value or the result of [block]
     */
    fun computeIfAbsent(key: Key, block: () -> Value): Value = lock.withLock {
        get(key)?.let { return it }

        val result = block()
        put(key, result)
        return result
    }
}
