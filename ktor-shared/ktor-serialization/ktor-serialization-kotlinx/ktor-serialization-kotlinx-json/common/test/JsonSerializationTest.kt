/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.test.dispatcher.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class JsonSerializationTest : AbstractSerializationTest<Json>() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val defaultSerializationFormat: Json = DefaultJson

    override fun assertEquals(expectedAsJson: String, actual: ByteArray, format: Json): Boolean {
        return expectedAsJson == actual.decodeToString()
    }

    @Test
    fun testJsonElements() = testSuspend {
        val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
        testSerializer.testSerialize(
            buildJsonObject {
                put("a", "1")
                put(
                    "b",
                    buildJsonObject {
                        put("c", 3)
                    }
                )
                put("x", JsonNull)
            }
        ).let { result ->
            assertEquals("""{"a":"1","b":{"c":3},"x":null}""", result.decodeToString())
        }

        testSerializer.testSerialize(
            buildJsonObject {
                put("a", "1")
                put(
                    "b",
                    buildJsonArray {
                        add("c")
                        add(JsonPrimitive(2))
                    }
                )
            }
        ).let { result ->
            assertEquals("""{"a":"1","b":["c",2]}""", result.decodeToString())
        }
    }
}
