package io.ktor.tests.utils

import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class StringValuesTest {
    @Test
    fun `single value map`() {
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
    fun `two value map`() {
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
    fun `three value case insensitive map`() {
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
    fun `add empty values list adds a key`() {
        val map = StringValues.build {
            appendAll("key", emptyList())
        }

        assertNotNull(map.getAll("key"))
        assertFalse { map.isEmpty() }
    }

    @Test
    fun `remove last should keep the key`() {
        val map = StringValues.build {
            append("key", "value")
            remove("key", "value")
        }

        assertNotNull(map.getAll("key"))
        assertFalse { map.isEmpty() }
    }

    @Test
    fun `filter`() {
        val map = StringValues.build(true) {
            append("Key1", "value1")
            append("Key1", "value2")
            append("Key1", "Value3")
        }.filter { _, value -> value.startsWith("V") }
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
    fun `appendFilter`() {
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

