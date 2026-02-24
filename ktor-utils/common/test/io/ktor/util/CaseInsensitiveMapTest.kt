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
    fun insertionOrderDoesNotOverflowOnChurn() {
        repeat(32) { i ->
            map["k$i"] = "v$i"
            map.remove("K$i")
        }
        assertTrue(map.isEmpty())

        map["final"] = "value"
        assertEquals("value", map["FINAL"])
    }

    @Test
    fun keySetOperationsAreCaseInsensitive() {
        map["Content-Type"] = "text/plain"

        assertTrue("content-type" in map.keys)
        assertTrue(map.keys.remove("CONTENT-TYPE"))
        assertTrue(map.isEmpty())
    }

    @Test
    fun equalsAndHashCodeAreCaseInsensitiveWithinMap() {
        val other = CaseInsensitiveMap<String>()
        map["Foo"] = "1"
        other["fOO"] = "1"

        assertEquals(map, other)
        assertEquals(map.hashCode(), other.hashCode())

        val regular = hashMapOf("Foo" to "1")
        assertTrue(map != regular)
    }
}
