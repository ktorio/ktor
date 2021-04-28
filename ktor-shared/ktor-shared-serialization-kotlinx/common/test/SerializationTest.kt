/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.common.serialization.*
import io.ktor.common.serialization.kotlinx.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@Serializable
data class GithubProfile(
    val login: String,
    val id: Int,
    val name: String
)

class SerializationTest {
    private val testSerializer = KotlinxSerializationConverter(DefaultJson)

    @Test
    fun testJsonElements() = testSuspend {
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
            assertEquals("""{"a":"1","b":{"c":3},"x":null}""", result)
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
            assertEquals("""{"a":"1","b":["c",2]}""", result)
        }
    }

    @Test
    fun testMapsElements() = testSuspend {
        testSerializer.testSerialize(
            mapOf(
                "a" to "1",
                "b" to "2"
            )
        ).let { result ->
            assertEquals("""{"a":"1","b":"2"}""", result)
        }

        testSerializer.testSerialize(
            mapOf(
                "a" to "1",
                "b" to null
            )
        ).let { result ->
            assertEquals("""{"a":"1","b":null}""", result)
        }

        testSerializer.testSerialize(
            mapOf(
                "a" to "1",
                null to "2"
            )
        ).let { result ->
            assertEquals("""{"a":"1",null:"2"}""", result)
        }

        // this is not yet supported
        assertFails {
            testSerializer.testSerialize<Map<String, Any>>(
                mapOf(
                    "a" to "1",
                    "b" to 2
                )
            )
        }
    }

    @Test
    fun testRegisterCustom() = testSuspend {
        val serializer = KotlinxSerializationConverter(DefaultJson)

        val user = User(1, "vasya")
        val actual = serializer.testSerialize(user)
        assertEquals("{\"id\":1,\"login\":\"vasya\"}", actual)
    }

    @Test
    fun testRegisterCustomList() = testSuspend {
        val serializer = KotlinxSerializationConverter(DefaultJson)

        val user = User(2, "petya")
        val photo = Photo(3, "petya.jpg")

        assertEquals("[{\"id\":2,\"login\":\"petya\"}]", serializer.testSerialize(listOf(user)))
        assertEquals("[{\"id\":3,\"path\":\"petya.jpg\"}]", serializer.testSerialize(listOf(photo)))
    }

    private suspend inline fun <reified T : Any> ContentConverter.testSerialize(data: T): String {
        val content = serialize(ContentType.Application.Json, Charsets.UTF_8, typeInfo<T>(), data)
        return (content as? TextContent)?.text ?: error("Failed to get serialized $data")
    }
}
