/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins.graphql

import io.ktor.server.plugins.graphql.*
import kotlinx.serialization.json.*
import kotlin.test.*

class JsonConversionTest {

    @Test
    fun `javaValueToJsonElement converts null`() {
        assertEquals(JsonNull, javaValueToJsonElement(null))
    }

    @Test
    fun `javaValueToJsonElement converts string`() {
        assertEquals(JsonPrimitive("hello"), javaValueToJsonElement("hello"))
    }

    @Test
    fun `javaValueToJsonElement converts boolean`() {
        assertEquals(JsonPrimitive(true), javaValueToJsonElement(true))
        assertEquals(JsonPrimitive(false), javaValueToJsonElement(false))
    }

    @Test
    fun `javaValueToJsonElement converts integers`() {
        assertEquals(JsonPrimitive(42), javaValueToJsonElement(42))
    }

    @Test
    fun `javaValueToJsonElement converts doubles`() {
        assertEquals(JsonPrimitive(3.14), javaValueToJsonElement(3.14))
    }

    @Test
    fun `javaValueToJsonElement converts nested maps`() {
        val input = mapOf("key" to "value", "nested" to mapOf("inner" to 1))
        val expected = JsonObject(
            mapOf(
                "key" to JsonPrimitive("value"),
                "nested" to JsonObject(mapOf("inner" to JsonPrimitive(1)))
            )
        )
        assertEquals(expected, javaValueToJsonElement(input))
    }

    @Test
    fun `javaValueToJsonElement converts lists`() {
        val input = listOf(1, "two", null)
        val expected = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("two"), JsonNull))
        assertEquals(expected, javaValueToJsonElement(input))
    }

    @Test
    fun `jsonElementToJavaValue converts string`() {
        assertEquals("hello", jsonElementToJavaValue(JsonPrimitive("hello")))
    }

    @Test
    fun `jsonElementToJavaValue converts boolean`() {
        assertEquals(true, jsonElementToJavaValue(JsonPrimitive(true)))
        assertEquals(false, jsonElementToJavaValue(JsonPrimitive(false)))
    }

    @Test
    fun `jsonElementToJavaValue converts integer`() {
        assertEquals(42L, jsonElementToJavaValue(JsonPrimitive(42)))
    }

    @Test
    fun `jsonElementToJavaValue converts double`() {
        assertEquals(3.14, jsonElementToJavaValue(JsonPrimitive(3.14)))
    }

    @Test
    fun `jsonElementToJavaValue converts null`() {
        assertNull(jsonElementToJavaValue(JsonNull))
    }

    @Test
    fun `jsonElementToJavaValue converts objects`() {
        val input = JsonObject(mapOf("key" to JsonPrimitive("value")))
        val result = jsonElementToJavaValue(input) as Map<*, *>
        assertEquals("value", result["key"])
    }

    @Test
    fun `jsonElementToJavaValue converts arrays`() {
        val input = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))
        val result = jsonElementToJavaValue(input) as List<*>
        assertEquals(listOf(1L, 2L), result)
    }
}
