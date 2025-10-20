/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.response

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationResponseFunctionsJvmTest {

    @Test
    fun testRespondResourceWithExistingResource() = testApplication {
        routing {
            get("/resource") {
                call.respondResource("testresource.txt", "testdir")
            }
        }

        val response = client.get("/resource")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Test resource content", response.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    }

    @Test
    fun testRespondResourceWithNonExistingResource() = testApplication {
        routing {
            get("/resource") {
                call.respondResource("nonexistent.txt")
            }
        }

        val response = client.get("/resource")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun testRespondResourceWithPackage() = testApplication {
        routing {
            get("/resource") {
                call.respondResource("testresource.txt", resourcePackage = "testdir")
            }
        }

        val response = client.get("/resource")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Test resource content", response.bodyAsText().trim())
    }

    @Test
    fun testRespondResourceWithDifferentContentTypes() = testApplication {
        routing {
            get("/html") {
                call.respondResource("test.html", "testdir")
            }
            get("/json") {
                call.respondResource("test.json", "testdir")
            }
        }

        val htmlResponse = client.get("/html")
        assertEquals(HttpStatusCode.OK, htmlResponse.status)
        assertEquals(ContentType.Text.Html, htmlResponse.contentType()?.withoutParameters())

        val jsonResponse = client.get("/json")
        assertEquals(HttpStatusCode.OK, jsonResponse.status)
        assertEquals(ContentType.Application.Json, jsonResponse.contentType()?.withoutParameters())
    }

    @Test
    fun testRespondResourceNotFoundWithPackage() = testApplication {
        routing {
            get("/resource") {
                call.respondResource("testresource.txt", resourcePackage = "com.example")
            }
        }

        val response = client.get("/resource")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun testRespondResourceFromRootPackage() = testApplication {
        routing {
            get("/resource") {
                call.respondResource("logback-test.xml")
            }
        }

        val response = client.get("/resource")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("logback"))
    }
}
