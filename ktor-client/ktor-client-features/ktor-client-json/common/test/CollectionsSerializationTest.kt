/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CollectionsSerializationTest {
    private val testSerializer = KotlinxSerializer()

    @Test
    fun testJsonElements() {
        testSerializer.testWrite(buildJsonObject {
            put("a", "1")
            put("b", buildJsonObject {
                put("c", 3)
            })
            put("x", JsonNull)
        }).let { result ->
            assertEquals("""{"a":"1","b":{"c":3},"x":null}""", result)
        }

        testSerializer.testWrite(buildJsonObject {
            put("a", "1")
            put("b", buildJsonArray {
                add("c")
                add(JsonPrimitive(2))
            })
        }).let { result ->
            assertEquals("""{"a":"1","b":["c",2]}""", result)
        }
    }

    @Test
    fun testMapsElements() {
        testSerializer.testWrite(mapOf(
            "a" to "1",
            "b" to "2"
        )).let { result ->
            assertEquals("""{"a":"1","b":"2"}""", result)
        }

        testSerializer.testWrite(mapOf(
            "a" to "1",
            "b" to null
        )).let { result ->
            assertEquals("""{"a":"1","b":null}""", result)
        }

        testSerializer.testWrite(mapOf(
            "a" to "1",
            null to "2"
        )).let { result ->
            assertEquals("""{"a":"1",null:"2"}""", result)
        }

        // this is not yet supported
        assertFails {
            testSerializer.testWrite(mapOf(
                "a" to "1",
                "b" to 2
            ))
        }
    }

    private fun JsonSerializer.testWrite(data: Any): String =
        (write(data, ContentType.Application.Json) as? TextContent)?.text ?: error("Failed to get serialized $data")
}
