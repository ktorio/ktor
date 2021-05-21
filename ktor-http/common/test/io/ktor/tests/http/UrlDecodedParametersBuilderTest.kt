/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class UrlDecodedParametersBuilderTest {

    @Test
    fun testMethodsDelegateToEncoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        encoded.append("key", "value")
        assertFalse(decoded.isEmpty())

        encoded.clear()
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun testAppendDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.append("ke y", "valu e")
        assertEquals("valu+e", encoded["ke%20y"])
        assertEquals("valu e", decoded["ke y"])
    }

    @Test
    fun testAppendAllDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.appendAll("ke y", listOf("valu e", "v%alue"))
        assertEquals(listOf("valu+e", "v%25alue"), encoded.getAll("ke%20y"))
        assertEquals(listOf("valu e", "v%alue"), decoded.getAll("ke y"))
    }

    @Test
    fun testContainsDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.append("ke y", "valu e")
        assertTrue(encoded.contains("ke%20y"))
        assertTrue(decoded.contains("ke y"))
        assertTrue(encoded.contains("ke%20y", "valu+e"))
        assertTrue(decoded.contains("ke y", "valu e"))
        assertFalse(encoded.contains("ke%20y", "valu+e1"))
        assertFalse(decoded.contains("ke y", "valu e1"))
    }

    @Test
    fun testNamesDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y1", "valu e1")
        assertEquals(setOf("ke%20y", "ke%20y1"), encoded.names())
        assertEquals(setOf("ke y", "ke y1"), decoded.names())
    }

    @Test
    fun testRemoveDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y", "valu e1")
        decoded.remove("ke y", "valu e1")
        assertTrue(encoded.contains("ke%20y", "valu+e"))
        assertFalse(encoded.contains("ke%20y", "valu+e1"))
        decoded.remove("ke y")
        assertFalse(encoded.contains("ke%20y"))
    }

    @Test
    fun testEntriesDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.append("ke y", "valu e")
        decoded.append("ke y", "valu e1")
        decoded.append("ke y1", "valu e1")
        val entriesEncoded = encoded.entries()
        val entriesDecoded = decoded.entries()

        assertEquals(2, entriesEncoded.size)
        assertEquals(2, entriesDecoded.size)

        assertEquals(listOf("valu+e", "valu+e1"), entriesEncoded.single { it.key == "ke%20y" }.value)
        assertEquals(listOf("valu+e1"), entriesEncoded.single { it.key == "ke%20y1" }.value)

        assertEquals(listOf("valu e", "valu e1"), entriesDecoded.single { it.key == "ke y" }.value)
        assertEquals(listOf("valu e1"), entriesDecoded.single { it.key == "ke y1" }.value)
    }

    @Test
    fun testAppendStringValueDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        val values = parametersOf("ke y" to listOf("valu e", "valu e1"), "ke y1" to listOf("valu e1"))
        decoded.appendAll(values)

        assertEquals(2, encoded.entries().size)
        assertEquals(2, decoded.entries().size)

        assertEquals(listOf("valu+e", "valu+e1"), encoded.getAll("ke%20y"))
        assertEquals(listOf("valu+e1"), encoded.getAll("ke%20y1"))

        assertEquals(listOf("valu e", "valu e1"), decoded.getAll("ke y"))
        assertEquals(listOf("valu e1"), decoded.getAll("ke y1"))
    }

    @Test
    fun testBuildDecoded() {
        val encoded = ParametersBuilder()
        val decoded = UrlDecodedParametersBuilder(encoded)

        decoded.appendAll("ke y", listOf("valu e", "valu e1"))
        decoded.appendAll("ke y1", listOf("valu e1"))

        val result = decoded.build()

        assertEquals(listOf("valu e", "valu e1"), result.getAll("ke y"))
        assertEquals(listOf("valu e1"), result.getAll("ke y1"))
    }
}
