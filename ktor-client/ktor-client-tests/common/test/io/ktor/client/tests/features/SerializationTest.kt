/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.features

import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.test.*

@Serializable
class MyCustomObject(val message: String)

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
}
