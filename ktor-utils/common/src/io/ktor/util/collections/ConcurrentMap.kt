/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*
import io.ktor.util.collections.internal.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*

private const val INITIAL_CAPACITY = 32
private const val MAX_LOAD_FACTOR = 0.5
private const val UPSIZE_RATIO = 2

/**
 * Ktor concurrent map implementation. Please do not use it.
 */
@InternalAPI
public class ConcurrentMap<Key : Any, Value : Any>(
    private val lock: Lock = Lock(),
    initialCapacity: Int = INITIAL_CAPACITY
) : MutableMap<Key, Value> {

    constructor(lock: Lock, map: Map<Key, Value>) : this(lock, map.size) {
        putAll(map)
    }

    private var table by shared(
        SharedList<SharedForwardList<MapItem<Key, Value>>>(initialCapacity)
    )

    private val _size = atomic(0)

    private var appendSize by shared(0)
    private val loadFactor get() = appendSize.toFloat() / table.size

    init {
        makeShared()
    }

    override val size: Int
        get() = _size.value

    override fun containsKey(key: Key): Boolean = get(key) != null

    override fun containsValue(value: Value): Boolean = locked {
        for (bucket in table) {
            bucket ?: continue

            for (item in bucket) {
                if (item.value == value) {
                    return@locked true
                }
            }
        }

        return@locked false
    }

    override fun get(key: Key): Value? = locked {
        val bucket = findBucket(key) ?: return@locked null
        val item = bucket.find { it.key == key }

        return@locked item?.value
    }

    override fun isEmpty(): Boolean = size == 0

    override fun clear(): Unit = locked {
        table = SharedList(INITIAL_CAPACITY)
    }

    override fun put(key: Key, value: Value): Value? = locked {
        if (loadFactor > MAX_LOAD_FACTOR) {
            upsize()
        }

        val bucket = findOrCreateBucket(key)
        val item = bucket.find { it.key == key }

        if (item != null) {
            val oldValue = item.value
            item.value = value
            return@locked oldValue
        }

        bucket.appendHead(MapItem(key, value))

        appendSize += 1
        _size.incrementAndGet()

        return@locked null
    }

    override fun putAll(from: Map<out Key, Value>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun remove(key: Key): Value? = locked {
        val bucket = findBucket(key) ?: return@locked null

        with(bucket.iterator()) {
            while (hasNext()) {
                val item = next()

                if (item.key == key) {
                    val result = item.value
                    _size.decrementAndGet()
                    remove()

                    return@locked result
                }
            }
        }

        return@locked null
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Key, Value>>
        get() = MutableMapEntries(this)

    override val keys: MutableSet<Key>
        get() = ConcurrentMapKeys(this)

    override val values: MutableCollection<Value>
        get() = ConcurrentMapValues(this)

    internal fun iterator(): MutableIterator<MutableMap.MutableEntry<Key, Value>> =
        object : MutableIterator<MutableMap.MutableEntry<Key, Value>> {
            private val items = mutableListOf<MapItem<Key, Value>>()
            private var index by shared(0)

            init {
                locked {
                    table.forEach {
                        it ?: return@forEach
                        it.forEach {
                            items.add(it)
                        }
                    }
                }

                makeShared()
            }

            override fun hasNext(): Boolean = index < keys.size

            override fun next(): MutableMap.MutableEntry<Key, Value> = items[index++]

            override fun remove() {
                check(index > 0)
                remove(items[index - 1].key)
            }
        }

    /**
     * Perform concurrent insert.
     */
    @Deprecated(
        "This is accidentally does insert instead of get. Use computeIfAbsent or getOrElse instead.",
        level = DeprecationLevel.ERROR
    )
    fun getOrDefault(key: Key, block: () -> Value): Value = lock.withLock {
        return@withLock computeIfAbsent(key, block)
    }

    /**
     * Computes [block] and inserts result in map. The [block] will be evaluated at most once.
     */
    public fun computeIfAbsent(key: Key, block: () -> Value): Value = locked {
        val value = get(key)
        if (value != null) {
            return@locked value
        }
        val newValue = block()
        put(key, newValue)

        return@locked newValue
    }

    private fun findBucket(key: Key): SharedForwardList<MapItem<Key, Value>>? {
        val bucketId = key.hashCode() and (table.size - 1)
        return table[bucketId]
    }

    private fun findOrCreateBucket(key: Key): SharedForwardList<MapItem<Key, Value>> {
        val bucketId = key.hashCode() and (table.size - 1)
        val result = table[bucketId]

        if (result == null) {
            val bucket = SharedForwardList<MapItem<Key, Value>>()
            table[bucketId] = bucket
            return bucket
        }

        return result
    }

    private fun upsize() {
        val newTable = ConcurrentMap<Key, Value>(initialCapacity = table.size * UPSIZE_RATIO)
        newTable.putAll(this)

        table = newTable.table
        appendSize = size
    }

    private fun <T> locked(block: () -> T): T = lock.withLock { block() }
}

private class MapItem<Key, Value>(override val key: Key, value: Value) : MutableMap.MutableEntry<Key, Value> {
    override var value: Value by shared(value)
    val hash: Int = key.hashCode()

    init {
        makeShared()
    }

    override fun setValue(newValue: Value): Value {
        val result = value
        value = newValue
        return result
    }

    override fun toString(): String = "MapItem[$key, $value]"
}
