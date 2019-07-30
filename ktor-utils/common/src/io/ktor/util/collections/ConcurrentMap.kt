/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*
import kotlinx.io.core.*

@InternalAPI
class ConcurrentMap<Key, Value>(
    private val lock: Lock = Lock(),
    private val delegate: MutableMap<Key, Value> = mutableMapOf<Key, Value>()
) : MutableMap<Key, Value> {

    override val size: Int get() = lock.use { delegate.size }

    override fun containsKey(key: Key): Boolean = lock.use {
        delegate.containsKey(key)
    }

    override fun containsValue(value: Value): Boolean = lock.use {
        delegate.containsValue(value)
    }

    override fun get(key: Key): Value? = lock.use {
        delegate[key]
    }

    override fun isEmpty(): Boolean = lock.use {
        delegate.isEmpty()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>> = ConcurrentSet(delegate.entries, lock)

    override val keys: MutableSet<Key> = ConcurrentSet(delegate.keys, lock)

    override val values: MutableCollection<Value> = ConcurrentCollection(delegate.values, lock)

    override fun clear() = lock.use {
        delegate.clear()
    }

    override fun put(key: Key, value: Value): Value? = lock.use {
        delegate.put(key, value)
    }

    override fun putAll(from: Map<out Key, Value>) = lock.use {
        delegate.putAll(from)
    }

    override fun remove(key: Key): Value? = lock.use {
        delegate.remove(key)
    }

    /**
     * Perform concurrent insert.
     */
    fun getOrDefault(key: Key, block: () -> Value): Value = lock.use {
        get(key)?.let { return it }

        val result = block()
        put(key, result)
        return result
    }
}
