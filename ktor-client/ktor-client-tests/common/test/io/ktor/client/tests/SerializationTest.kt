/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

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
    fun testSendCustomObject() = clientTests {
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
}
