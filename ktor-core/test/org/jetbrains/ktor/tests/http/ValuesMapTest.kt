package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class ValuesMapTest {
    @Test
    fun `single value map`() {
        val map = ValuesMap.build {
            append("key1", "value1")
        }
        assertEquals("value1", map["key1"])
        assertEquals(null, map["key3"])
        assertTrue { map.contains("key1") }
        assertTrue { map.contains("key1", "value1") }
        assertFalse { map.contains("key1", "value3") }
    }

    @Test
    fun `two value map`() {
        val map = ValuesMap.build {
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
    fun `two value case insensitive map`() {
        val map = ValuesMap.build(true) {
            append("Key1", "value1")
            append("Key1", "value2")
            append("Key1", "Value3")
        }
        assertEquals("value1", map["key1"])
        assertEquals("value1", map["keY1"])
        assertEquals(setOf("key1"), map.names())
        assertEquals(listOf("key1" to listOf("value1", "value2", "Value3")), map.entries().map { it.key to it.value })
        assertEquals(listOf("value1", "value2", "Value3"), map.getAll("key1"))
        assertEquals(listOf("value1", "value2", "Value3"), map.getAll("kEy1"))
        assertTrue { map.contains("Key1") }
        assertTrue { map.contains("Key1", "value1") }
        assertTrue { map.contains("kEy1", "value2") }
        assertFalse { map.contains("kEy1", "value3") }
    }
}

