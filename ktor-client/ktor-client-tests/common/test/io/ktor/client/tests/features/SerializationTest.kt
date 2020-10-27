/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
class MyCustomObject(val message: String)

@Serializable
sealed class TestSealed {
    @Serializable
    @SerialName("A")
    data class A(val valA: String) : TestSealed()

    @Serializable
    @SerialName("B")
    data class B(val valB: String) : TestSealed()
}

@Serializable
class TestGeneric<T>(
    val id: Int,
    val data: T
)

class SerializationTest : ClientLoader() {
    @Test
    fun testSendCustomObject() = clientTests(listOf("native:CIO")) {
        config {
            install(JsonFeature)
        }

        test { client ->
            assertFailsWith<ClientRequestException> {
                client.post {
                    url("https://google.com")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    body = MyCustomObject(message = "Hello World")
                }
            }
        }
    }

    @Test
    fun testSendStringWithSerialization() = clientTests(listOf("native:CIO")) {
        config {
            install(JsonFeature)
        }

        test { client ->
            assertFailsWith<ClientRequestException> {
                client.post {
                    url("https://google.com")
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    body = "Hello, world"
                }
            }
        }
    }

    @Test
    fun testSendObjectWithoutContentType() = clientTests {
        config {
            install(JsonFeature)
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.post("$TEST_SERVER/json/object") {
                    body = MyCustomObject("Foo")
                }
            }
        }
    }

    @Test
    fun testPostSealedClass() = clientTests {
        config {
            install(JsonFeature)
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(listOf(TestSealed.A("a"), TestSealed.B("b")))
            }
            assertEquals("""[{"type":"A","valA":"a"},{"type":"B","valB":"b"}]""", response)
        }
    }

    @Test
    fun testPostGenericClass() = clientTests {
        config {
            install(JsonFeature)
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(TestGeneric(1, "test"))
            }
            assertEquals("""{"id":1,"data":"test"}""", response)
        }
    }
}
