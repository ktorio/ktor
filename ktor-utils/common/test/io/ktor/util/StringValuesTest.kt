/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlin.test.*

class StringValuesTest {
    @Test
    fun singleValueMap() {
        val map = StringValues.build {
            append("key1", "value1")
        }
        assertEquals("value1", map["key1"])
        assertEquals(null, map["key3"])
        assertTrue { map.contains("key1") }
        assertTrue { map.contains("key1", "value1") }
        assertFalse { map.contains("key1", "value3") }
    }

    @Test
    fun twoValueMap() {
        val map = StringValues.build {
            append("key1", "value1")
            append("key1", "value2")
        }
        assertEquals("value1", map["key1"])
        assertEquals(listOf("value1", "value2"), map.getAll("key1"))
        assertTrue { map.contains("key1") }
        assertTrue { map.contains("key1", "value1") }
        assertTrue { map.contains("key1", "value2") }
        assertFalse { map.contains("key1", "value3") }
    }

    @Test
    fun threeValueCaseInsensitiveMap() {
        val map = StringValues.build(true) {
            append("Key1", "value1")
            append("Key1", "value2")
            append("Key1", "Value3")
        }
        assertEquals("value1", map["key1"])
        assertEquals("value1", map["keY1"])
        assertEquals(setOf("Key1"), map.names())
        assertEquals(listOf("Key1" to listOf("value1", "value2", "Value3")), map.entries().map { it.key to it.value })
        assertEquals(listOf("value1", "value2", "Value3"), map.getAll("key1"))
        assertEquals(listOf("value1", "value2", "Value3"), map.getAll("kEy1"))
        assertTrue { map.contains("Key1") }
        assertTrue { map.contains("Key1", "value1") }
        assertTrue { map.contains("kEy1", "value2") }
        assertFalse { map.contains("kEy1", "value3") }
    }

    @Test
    fun addEmptyValuesListAddsKey() {
        val map = StringValues.build {
            appendAll("key", emptyList())
        }

        assertNotNull(map.getAll("key"))
        assertFalse { map.isEmpty() }
    }

    @Test
    fun removeLastShouldKeepTheKey() {
        val map = StringValues.build {
            append("key", "value")
            remove("key", "value")
        }

        assertNotNull(map.getAll("key"))
        assertFalse { map.isEmpty() }
    }

    @Test
    fun filter() {
        val map = StringValues.build(true) {
            append("Key1", "value1")
            append("Key1", "value2")
            append("Key1", "Value3")
        }.filter { _, value -> value.startsWith("V") }
        val names = map.names()
        setOf("Key1") == names
        assertEquals("Value3", map["key1"])
        assertEquals("Value3", map["keY1"])
        assertEquals(setOf("Key1"), map.names())
        assertEquals(listOf("Key1" to listOf("Value3")), map.entries().map { it.key to it.value })
        assertEquals(listOf("Value3"), map.getAll("key1"))
        assertEquals(listOf("Value3"), map.getAll("kEy1"))
        assertTrue { map.contains("Key1") }
        assertFalse { map.contains("Key1", "value1") }
        assertFalse { map.contains("kEy1", "value2") }
        assertFalse { map.contains("kEy1", "value3") }
    }

    @Test
    fun appendFilter() {
        val original = StringValues.build(true) {
            append("Key1", "value1")
            append("Key1", "value2")
            append("Key1", "Value3")
        }
        val map = StringValues.build(true) {
            appendFiltered(original) { _, value -> value.startsWith("V") }
        }

        assertEquals("Value3", map["key1"])
        assertEquals("Value3", map["keY1"])
        assertEquals(setOf("Key1"), map.names())
        assertEquals(listOf("Key1" to listOf("Value3")), map.entries().map { it.key to it.value })
        assertEquals(listOf("Value3"), map.getAll("key1"))
        assertEquals(listOf("Value3"), map.getAll("kEy1"))
        assertTrue { map.contains("Key1") }
        assertFalse { map.contains("Key1", "value1") }
        assertFalse { map.contains("kEy1", "value2") }
        assertFalse { map.contains("kEy1", "value3") }
    }
}
