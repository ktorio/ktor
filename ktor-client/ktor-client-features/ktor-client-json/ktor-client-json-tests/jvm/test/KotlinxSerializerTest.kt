/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.json.tests

import io.ktor.client.features.json.serializer.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.streams.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.test.*

class KotlinxSerializerTest {
    @Suppress("EXPERIMENTAL_API_USAGE_ERROR")
    @Test
    fun testCustomDeserializer() {
        val upwrapper = indexListUnwrapper<TestEntry>()

        val serializer = Json {
            ignoreUnknownKeys = true
            serializersModule = serializersModuleOf(upwrapper)
        }

        val kotlinxSerializer = KotlinxSerializer(serializer)
        val json = """
            {
                "something": "something",
                "data": [
                    {"a": "hello", "b": 42},
                    {"a": "bye", "b": 4242}
                ]
            }
        """.trimIndent().byteInputStream().asInput()

        @Suppress("UNCHECKED_CAST")
        val data = kotlinxSerializer.read(typeInfo<List<TestEntry>>(), json) as List<TestEntry>

        assertEquals(2, data.size)
        assertEquals(TestEntry("hello", 42), data[0])
        assertEquals(TestEntry("bye", 4242), data[1])
    }
}

@Serializable
data class TestEntry(val a: String, val b: Int)

inline fun <reified T> indexListUnwrapper() =
    object : JsonTransformingSerializer<List<T>>(ListSerializer<T>(serializer<T>())) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            return if (element is JsonArray) element else element.jsonObject.values.firstOrNull { it is JsonArray }
                ?: error("Collection not found in json")
        }
    }
