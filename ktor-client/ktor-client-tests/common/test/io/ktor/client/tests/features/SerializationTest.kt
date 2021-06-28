/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.features

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.shared.serialization.kotlinx.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
class MyCustomObject(val message: String)

class SerializationTest : ClientLoader() {
    @Test
    fun testSendCustomObject() = clientTests(listOf("native:CIO")) {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            client.post {
                url("$TEST_SERVER/echo")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(MyCustomObject(message = "Hello World"))
            }.let {
                assertEquals("""{"message":"Hello World"}""", it.bodyAsText())
            }
        }
    }

    @Test
    fun testSendStringWithSerialization() = clientTests(listOf("native:CIO")) {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            client.post {
                url("$TEST_SERVER/echo")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody("Hello, world")
            }.let {
                assertEquals("\"Hello, world\"", it.bodyAsText())
            }
        }
    }

    @Test
    fun testSendObjectWithoutContentType() = clientTests {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.post("$TEST_SERVER/json/object") {
                    setBody(MyCustomObject("Foo"))
                }
            }
        }
    }
}
