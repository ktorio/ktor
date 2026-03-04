/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingOptimizationTest {

    @Test
    fun `routing resolves correctly without trace logging`() = testApplication {
        routing {
            get("/hello") {
                call.respondText("world")
            }
            post("/echo") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        val getResponse = client.get("/hello")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals("world", getResponse.bodyAsText())

        val postResponse = client.post("/echo") {
            setBody("test input")
        }
        assertEquals(HttpStatusCode.OK, postResponse.status)
        assertEquals("test input", postResponse.bodyAsText())
    }

    @Test
    fun `nested routing resolves correctly`() = testApplication {
        routing {
            route("/api") {
                get("/users") {
                    call.respondText("users list")
                }
                route("/admin") {
                    get("/dashboard") {
                        call.respondText("admin dashboard")
                    }
                }
            }
        }

        val response = client.get("/api/users")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("users list", response.bodyAsText())

        val adminResponse = client.get("/api/admin/dashboard")
        assertEquals(HttpStatusCode.OK, adminResponse.status)
        assertEquals("admin dashboard", adminResponse.bodyAsText())
    }

    @Test
    fun `unmatched route returns 404`() = testApplication {
        routing {
            get("/exists") {
                call.respondText("found")
            }
        }

        val response = client.get("/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
