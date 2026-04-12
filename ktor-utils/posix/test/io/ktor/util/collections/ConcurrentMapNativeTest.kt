/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import kotlin.test.*

class ConcurrentMapNativeTest {

    @Test
    fun `entries returns snapshot`() {
        val map = ConcurrentMap<Int, String>(initialCapacity = 4)
        map[1] = "one"
        map[2] = "two"

        val entries = map.entries
        map.remove(1)
        map[3] = "three"

        assertEquals(listOf(1 to "one", 2 to "two"), entries.map { it.key to it.value }.sortedBy { it.first })
    }

    @Test
    fun `keys returns snapshot`() {
        val map = ConcurrentMap<Int, String>(initialCapacity = 4)
        map[1] = "one"
        map[2] = "two"

        val keys = map.keys
        map.remove(1)
        map[3] = "three"

        assertEquals(setOf(1, 2), keys.toSet())
    }

    @Test
    fun `values returns snapshot`() {
        val map = ConcurrentMap<Int, String>(initialCapacity = 4)
        map[1] = "one"
        map[2] = "two"

        val values = map.values
        map.remove(1)
        map[3] = "three"

        assertEquals(listOf("one", "two"), values.sorted())
    }

    @Test
    fun `entries snapshot supports value-based contains and remove`() {
        val map = ConcurrentMap<Int, String>(initialCapacity = 4)
        map[1] = "one"
        map[2] = "two"

        val entries = map.entries
        assertTrue(entries.contains(TestMutableEntry(1, "one")))
        assertTrue(entries.remove(TestMutableEntry(1, "one")))
        assertFalse(entries.contains(TestMutableEntry(1, "one")))
    }
}

private class TestMutableEntry<K, V>(
    override val key: K,
    override val value: V
) : MutableMap.MutableEntry<K, V> {
    override fun setValue(newValue: V): V = value

    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false
        return key == other.key && value == other.value
    }

    override fun hashCode(): Int = key.hashCode() xor value.hashCode()
}
