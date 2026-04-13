/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RequireRequestParameterTest {

    @Test
    fun `requireQueryParameter returns value when present`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireQueryParameter("name")
                call.respondText(value)
            }
        }

        val response = client.get("/test?name=hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("hello", response.bodyAsText())
    }

    @Test
    fun `requireQueryParameter returns 400 when missing`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireQueryParameter("name")
                call.respondText(value)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `requireHeader returns value when present`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireHeader("X-Custom")
                call.respondText(value)
            }
        }

        val response = client.get("/test") {
            header("X-Custom", "myvalue")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("myvalue", response.bodyAsText())
    }

    @Test
    fun `requireHeader returns 400 when missing`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireHeader("X-Custom")
                call.respondText(value)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `requireCookie returns value when present`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireCookie("session")
                call.respondText(value)
            }
        }

        val response = client.get("/test") {
            header(HttpHeaders.Cookie, "session=abc123")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("abc123", response.bodyAsText())
    }

    @Test
    fun `requireCookie returns 400 when missing`() = testApplication {
        routing {
            get("/test") {
                val value = call.requireCookie("session")
                call.respondText(value)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `requirePathParameter returns value when present`() = testApplication {
        routing {
            get("/users/{id}") {
                val value = call.requirePathParameter("id")
                call.respondText(value)
            }
        }

        val response = client.get("/users/42")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("42", response.bodyAsText())
    }

    @Test
    fun `requirePathParameter returns 400 when missing`() = testApplication {
        routing {
            get("/test") {
                val value = call.requirePathParameter("id")
                call.respondText(value)
            }
        }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
