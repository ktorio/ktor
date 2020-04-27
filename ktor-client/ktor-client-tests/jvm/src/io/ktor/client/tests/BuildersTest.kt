/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
abstract class BuildersTest(val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/empty") {
                call.respondText("")
            }
            get("/hello") {
                call.respondText("hello")
            }
        }
    }

    @Test
    fun getEmptyResponseTest() = testWithEngine(factory) {
        test { client ->
            val response = client.get<String>(path = "/empty", port = serverPort)
            assertEquals("", response)
        }
    }

    @Test
    fun testNotFound() = testWithEngine(factory) {
        test { client ->
            assertFailsWith<ResponseException> {
                client.get<String>(path = "/notFound", port = serverPort)
            }
        }
    }

    @Test
    fun testDefaultRequest() = testWithEngine(factory) {
        test { rawClient ->

            val client = rawClient.config {
                defaultRequest {
                    port = serverPort
                }
            }

            assertEquals("hello", client.get<String>(path = "hello"))
        }
    }
}
