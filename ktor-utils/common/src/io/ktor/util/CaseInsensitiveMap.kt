/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * A map with case-insensitive [String] keys.
 *
 * Uses open-addressing hash table with case-insensitive hash and equals
 * to avoid wrapper object allocations on every lookup.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.CaseInsensitiveMap)
 */
public class CaseInsensitiveMap<Value : Any> : MutableMap<String, Value> {
    private var keyStorage: Array<String?> = arrayOfNulls(INITIAL_CAPACITY)
    private var valueStorage: Array<Any?> = arrayOfNulls(INITIAL_CAPACITY)
    private var _size: Int = 0

    // Track insertion order for iteration (like LinkedHashMap)
    private var insertionOrder: IntArray = IntArray(INITIAL_CAPACITY) { -1 }
    private var insertionCount: Int = 0

    override val size: Int get() = _size

    override fun containsKey(key: String): Boolean = findIndex(key) >= 0

    override fun containsValue(value: Value): Boolean {
        for (i in valueStorage.indices) {
            if (keyStorage[i] != null && valueStorage[i] == value) return true
        }
        return false
    }

    override fun get(key: String): Value? {
        val index = findIndex(key)
        @Suppress("UNCHECKED_CAST")
        return if (index >= 0) valueStorage[index] as Value? else null
    }

    override fun isEmpty(): Boolean = _size == 0

    override fun clear() {
        keyStorage.fill(null)
        valueStorage.fill(null)
        insertionOrder.fill(-1)
        _size = 0
        insertionCount = 0
    }

    override fun put(key: String, value: Value): Value? {
        ensureCapacity()
        val hash = caseInsensitiveHashCode(key)
        var index = hash and (keyStorage.size - 1)

        while (true) {
            val existingKey = keyStorage[index]
            if (existingKey == null) {
                if (insertionCount == insertionOrder.size) {
                    compactInsertionOrder()
                }
                // Empty slot, insert here
                keyStorage[index] = key
                valueStorage[index] = value
                // Track insertion order
                insertionOrder[insertionCount++] = index
                _size++
                return null
            }
            if (existingKey.equals(key, ignoreCase = true)) {
                // Key exists, replace value
                @Suppress("UNCHECKED_CAST")
                val oldValue = valueStorage[index] as Value?
                valueStorage[index] = value
                return oldValue
            }
            // Linear probing
            index = (index + 1) and (keyStorage.size - 1)
        }
    }

    override fun putAll(from: Map<out String, Value>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun remove(key: String): Value? {
        val index = findIndex(key)
        if (index < 0) return null

        @Suppress("UNCHECKED_CAST")
        val oldValue = valueStorage[index] as Value?

        // Invalidate the insertionOrder entry for the removed index to prevent duplicate iteration
        for (i in 0 until insertionCount) {
            if (insertionOrder[i] == index) {
                insertionOrder[i] = -1
                break
            }
        }

        // Mark as deleted and rehash following entries
        keyStorage[index] = null
        valueStorage[index] = null
        _size--

        // Rehash entries that might have been displaced
        // For rehashed entries, update their insertionOrder to point to the new index
        var nextIndex = (index + 1) and (keyStorage.size - 1)
        while (keyStorage[nextIndex] != null) {
            val rehashKey = keyStorage[nextIndex]!!
            val rehashValue = valueStorage[nextIndex]
            val oldRehashIndex = nextIndex

            keyStorage[nextIndex] = null
            valueStorage[nextIndex] = null
            _size--
            @Suppress("UNCHECKED_CAST")
            val newIndex = putWithoutTrackingOrderReturnIndex(rehashKey, rehashValue as Value)

            // Update insertionOrder to point to new index (not invalidate)
            for (i in 0 until insertionCount) {
                if (insertionOrder[i] == oldRehashIndex) {
                    insertionOrder[i] = newIndex
                    break
                }
            }

            nextIndex = (nextIndex + 1) and (keyStorage.size - 1)
        }

        return oldValue
    }

    /**
     * Internal put that doesn't track insertion order.
     * Used during rehashing in remove() to avoid duplicate entries in insertionOrder.
     * Returns the index where the entry was placed.
     */
    private fun putWithoutTrackingOrderReturnIndex(key: String, value: Value): Int {
        val hash = caseInsensitiveHashCode(key)
        var index = hash and (keyStorage.size - 1)

        while (true) {
            val existingKey = keyStorage[index]
            if (existingKey == null) {
                keyStorage[index] = key
                valueStorage[index] = value
                _size++
                return index
            }
            if (existingKey.equals(key, ignoreCase = true)) {
                valueStorage[index] = value
                return index
            }
            index = (index + 1) and (keyStorage.size - 1)
        }
    }

    override val keys: MutableSet<String>
        get() = KeySet()

    override val entries: MutableSet<MutableMap.MutableEntry<String, Value>>
        get() = EntrySet()

    override val values: MutableCollection<Value>
        get() = ValueCollection()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is CaseInsensitiveMap<*>) return false
        if (other.size != _size) return false
        for (i in keyStorage.indices) {
            val k = keyStorage[i]
            if (k != null) {
                val v = valueStorage[i]
                if (other[k] != v) return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 0
        for (i in keyStorage.indices) {
            val k = keyStorage[i]
            if (k != null) {
                result += caseInsensitiveHashCode(k) xor (valueStorage[i]?.hashCode() ?: 0)
            }
        }
        return result
    }

    private fun findIndex(key: String): Int {
        val hash = caseInsensitiveHashCode(key)
        var index = hash and (keyStorage.size - 1)

        while (true) {
            val existingKey = keyStorage[index]
            if (existingKey == null) return -1
            if (existingKey.equals(key, ignoreCase = true)) return index
            index = (index + 1) and (keyStorage.size - 1)
        }
    }

    private fun ensureCapacity() {
        // Resize when load factor > 0.75
        if (_size * 4 >= keyStorage.size * 3) {
            resize(keyStorage.size * 2)
        }
    }

    private fun resize(newCapacity: Int) {
        val oldKeys = keyStorage
        val oldValues = valueStorage
        val oldInsertionOrder = insertionOrder
        val oldInsertionCount = insertionCount

        keyStorage = arrayOfNulls(newCapacity)
        valueStorage = arrayOfNulls(newCapacity)
        insertionOrder = IntArray(newCapacity) { -1 }
        _size = 0
        insertionCount = 0

        // Reinsert in original insertion order to maintain iteration order
        for (i in 0 until oldInsertionCount) {
            val oldIndex = oldInsertionOrder[i]
            if (oldIndex >= 0 && oldKeys[oldIndex] != null) {
                @Suppress("UNCHECKED_CAST")
                put(oldKeys[oldIndex]!!, oldValues[oldIndex] as Value)
            }
        }
    }

    private fun compactInsertionOrder() {
        if (insertionCount == 0) return
        var writeIndex = 0
        for (i in 0 until insertionCount) {
            val idx = insertionOrder[i]
            if (idx >= 0 && keyStorage[idx] != null) {
                insertionOrder[writeIndex++] = idx
            }
        }
        for (i in writeIndex until insertionOrder.size) {
            insertionOrder[i] = -1
        }
        insertionCount = writeIndex
    }

    private inner class KeySet : AbstractMutableSet<String>() {
        override val size: Int get() = _size

        override fun add(element: String): Boolean =
            throw UnsupportedOperationException()

        override fun contains(element: String): Boolean =
            this@CaseInsensitiveMap.containsKey(element)

        override fun remove(element: String): Boolean =
            this@CaseInsensitiveMap.remove(element) != null

        override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
            private var orderIndex = 0
            private var lastKey: String? = null

            init {
                advance()
            }

            private fun advance() {
                // Skip removed entries (where the key at that index is now null)
                while (orderIndex < insertionCount) {
                    val idx = insertionOrder[orderIndex]
                    if (idx >= 0 && keyStorage[idx] != null) break
                    orderIndex++
                }
            }

            override fun hasNext(): Boolean = orderIndex < insertionCount

            override fun next(): String {
                if (!hasNext()) throw NoSuchElementException()
                val idx = insertionOrder[orderIndex]
                lastKey = keyStorage[idx]!!
                orderIndex++
                advance()
                return lastKey!!
            }

            override fun remove() {
                val key = lastKey ?: throw IllegalStateException()
                this@CaseInsensitiveMap.remove(key)
                lastKey = null
            }
        }
    }

    private inner class ValueCollection : AbstractMutableCollection<Value>() {
        override val size: Int get() = _size

        override fun add(element: Value): Boolean =
            throw UnsupportedOperationException()

        override fun iterator(): MutableIterator<Value> = object : MutableIterator<Value> {
            private var orderIndex = 0
            private var lastKey: String? = null

            init {
                advance()
            }

            private fun advance() {
                while (orderIndex < insertionCount) {
                    val idx = insertionOrder[orderIndex]
                    if (idx >= 0 && keyStorage[idx] != null) break
                    orderIndex++
                }
            }

            override fun hasNext(): Boolean = orderIndex < insertionCount

            override fun next(): Value {
                if (!hasNext()) throw NoSuchElementException()
                val idx = insertionOrder[orderIndex]
                lastKey = keyStorage[idx]!!
                @Suppress("UNCHECKED_CAST")
                val result = valueStorage[idx] as Value
                orderIndex++
                advance()
                return result
            }

            override fun remove() {
                val key = lastKey ?: throw IllegalStateException()
                this@CaseInsensitiveMap.remove(key)
                lastKey = null
            }
        }
    }

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<String, Value>>() {
        override val size: Int get() = _size

        override fun add(element: MutableMap.MutableEntry<String, Value>): Boolean =
            throw UnsupportedOperationException()

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<String, Value>> =
            object : MutableIterator<MutableMap.MutableEntry<String, Value>> {
                private var orderIndex = 0
                private var lastKey: String? = null

                init {
                    advance()
                }

                private fun advance() {
                    while (orderIndex < insertionCount) {
                        val idx = insertionOrder[orderIndex]
                        if (idx >= 0 && keyStorage[idx] != null) break
                        orderIndex++
                    }
                }

                override fun hasNext(): Boolean = orderIndex < insertionCount

                override fun next(): MutableMap.MutableEntry<String, Value> {
                    if (!hasNext()) throw NoSuchElementException()
                    val idx = insertionOrder[orderIndex]
                    lastKey = keyStorage[idx]!!
                    val entry = MapEntry(idx)
                    orderIndex++
                    advance()
                    return entry
                }

                override fun remove() {
                    val key = lastKey ?: throw IllegalStateException()
                    this@CaseInsensitiveMap.remove(key)
                    lastKey = null
                }
            }
    }

    private inner class MapEntry(private val index: Int) : MutableMap.MutableEntry<String, Value> {
        override val key: String get() = keyStorage[index]!!

        @Suppress("UNCHECKED_CAST")
        override val value: Value get() = valueStorage[index] as Value

        override fun setValue(newValue: Value): Value {
            @Suppress("UNCHECKED_CAST")
            val old = valueStorage[index] as Value
            valueStorage[index] = newValue
            return old
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key.equals(other.key as? String ?: return false, ignoreCase = true) &&
                value == other.value
        }

        override fun hashCode(): Int = caseInsensitiveHashCode(key) xor value.hashCode()

        override fun toString(): String = "$key=$value"
    }

    private companion object {
        private const val INITIAL_CAPACITY = 16

        /**
         * Computes case-insensitive hash code inline without allocating wrapper objects.
         */
        private fun caseInsensitiveHashCode(s: String): Int {
            var h = 0
            for (i in 0 until s.length) {
                h = 31 * h + s[i].lowercaseChar().code
            }
            return h
        }
    }
}
