/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor

import io.ktor.client.features.*
import io.ktor.client.features.cbor.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@ExperimentalSerializationApi
class KotlinxSerializerTest : ClientLoader() {
    private val cbor = Cbor

    @Test
    fun testRegisterCustom() {
        val serializer = KotlinxSerializer()

        val user = User(1, "vasya")
        val actual = serializer.testWrite(user)
        assertEquals(user, cbor.decodeFromByteArray(actual))
    }

    @Test
    fun testRegisterCustomList() {
        val serializer = KotlinxSerializer()

        val user = User(2, "petya")
        val photo = Photo(3, "petya.jpg")

        assertEquals(listOf(user), cbor.decodeFromByteArray(serializer.testWrite(listOf(user))))
        assertEquals(listOf(photo), cbor.decodeFromByteArray(serializer.testWrite(listOf(photo))))
    }

    @Test
    fun testCustomFormBody() = clientTests {
        config {
            install(CborFeature)
        }

        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeText("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        test { client ->
            var throwed = false
            try {
                client.submitFormWithBinaryData<String>(url = "upload", formData = data())
            } catch (cause: Throwable) {
                throwed = true
            }

            assertTrue(throwed, "Connection exception expected.")
        }
    }

    @Test
    fun testStringWithCborFeature() = clientTests {
        config {
            install(CborFeature)
            defaultRequest {
                val contentType = ContentType.parse("application/vnd.string+cbor")
                accept(contentType)
                contentType(contentType)
            }
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo-with-content-type") {
                body = "Hello"
            }
            assertEquals("eHello", response)

            val textResponse = client.post<String>("$TEST_SERVER/echo") {
                body = "Hello"
            }
            assertEquals("eHello", textResponse)

            val emptyResponse = client.post<String>("$TEST_SERVER/echo")
            assertEquals("", emptyResponse)
        }
    }

    @Test
    fun testMultipleListSerializersWithClient() = clientTests {
        val testSerializer = KotlinxSerializer()

        config {
            install(CborFeature) {
                serializer = testSerializer
            }
            defaultRequest {
                accept(ContentType.Application.Cbor)
            }
        }

        test { client ->
            val users = client.get<List<User>>("$TEST_SERVER/cbor/users")
            val photos = client.get<List<Photo>>("$TEST_SERVER/cbor/photos")

            assertEquals(listOf(User(42, "TestLogin")), users)
            assertEquals(listOf(Photo(4242, "cat.jpg")), photos)
        }
    }

    private fun CborSerializer.testWrite(data: Any): ByteArray =
        (write(data, ContentType.Application.Cbor) as? ByteArrayContent)?.bytes()
            ?: error("Failed to get serialized $data")
}
