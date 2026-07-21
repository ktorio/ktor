/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlin.test.*

class CaseInsensitiveMapTest {
    val map = CaseInsensitiveMap<String>()

    @Test
    fun smokeTest() {
        map["aA"] = "a"

        assertEquals("a", map["aA"])
        assertEquals("a", map["aa"])
        assertEquals("a", map["AA"])
        assertEquals("a", map["Aa"])

        map["someLongKey-547575458645845158458614864864586458651861861861986148"] = "8"
        assertEquals("8", map["someLongKey-547575458645845158458614864864586458651861861861986148"])
        assertEquals("8", map["somelongkey-547575458645845158458614864864586458651861861861986148"])
        assertEquals("8", map["somelongKey-547575458645845158458614864864586458651861861861986148"])

        map["Content-Type"] = "text/plain"
        assertEquals("text/plain", map["Content-Type"])
    }

    @Test
    fun `insertion order does not overflow on churn`() {
        repeat(32) { i ->
            map["k$i"] = "v$i"
            map.remove("K$i")
        }
        assertTrue(map.isEmpty())

        map["final"] = "value"
        assertEquals("value", map["FINAL"])
    }

    @Test
    fun `keySet operations are case-insensitive`() {
        map["Content-Type"] = "text/plain"

        assertTrue("content-type" in map.keys)
        assertTrue(map.keys.remove("CONTENT-TYPE"))
        assertTrue(map.isEmpty())
    }

    @Test
    fun `equals and hashCode are case-insensitive within map`() {
        val other = CaseInsensitiveMap<String>()
        map["Foo"] = "1"
        other["fOO"] = "1"

        assertEquals(map, other)
        assertEquals(map.hashCode(), other.hashCode())

        // CaseInsensitiveMap.equals rejects non-CaseInsensitiveMap instances to
        // preserve the equals/hashCode contract (case-insensitive hashing
        // would differ from regular map hashing).
        val regular = hashMapOf("Foo" to "1")
        assertFalse(map.equals(regular), "CaseInsensitiveMap should not equal a regular map")
    }

    @Test
    fun `MapEntry equals uses standard key equality`() {
        map["Foo"] = "bar"
        val entry = map.entries.first()
        val sameRegularEntry = regularEntry("Foo", "bar")
        val differentRegularEntry = regularEntry("foo", "bar")

        // Entry equality is based on the actual stored key, not case-insensitive lookup
        assertNotEquals(entry, differentRegularEntry)
        assertNotEquals(differentRegularEntry, entry)
        assertEquals(entry, sameRegularEntry)
        assertEquals(sameRegularEntry, entry)
    }

    @Test
    fun `MapEntry equals and hashCode satisfy the equals-hashCode contract`() {
        map["Foo"] = "bar"
        val entry = map.entries.first()
        val regularEntry = regularEntry("Foo", "bar")

        assertEquals(entry, regularEntry)
        assertEquals(entry.hashCode(), regularEntry.hashCode())
    }

    @Test
    fun `MapEntry remains valid after map remove triggers rehash`() {
        // "a" and "i" both hash to slot 1 with INITIAL_CAPACITY=8
        // ('a'.code=97, 97 and 7=1; 'i'.code=105, 105 and 7=1), so "i" is displaced to slot 2.
        map["a"] = "1" // goes to slot 1
        map["i"] = "2" // collides, displaced to slot 2

        val iEntry = map.entries.first { it.key == "i" }
        assertEquals("i", iEntry.key)
        assertEquals("2", iEntry.value)

        // Removing "a" triggers rehash: "i" moves from slot 2 to slot 1.
        map.remove("a") // rehashes "i" from slot 2 → slot 1

        // Entry holds last-known value after removal.
        assertEquals("i", iEntry.key)
        assertEquals("2", iEntry.value)
    }

    private fun <K, V> regularEntry(key: K, value: V): Map.Entry<K, V> = mapOf(key to value).entries.single()
}
