/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.testing.*
import kotlin.test.*

class HandlerTest {

    @Test
    fun `application with empty handler`() = withTestApplication {
        on("making a request") {
            val call = handleRequest { }
            it("should not be handled") {
                assertFalse(call.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun `application with transparent handler`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {}
        on("making a request") {
            val call = handleRequest { }
            it("should not be handled") {
                assertFalse(call.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun `application with handler returning true`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) { call.respond(HttpStatusCode.OK) }
        on("making a request") {
            val call = handleRequest { }
            it("should be handled") {
                assertTrue(call.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun `application with handler that checks body on POST method`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            if (call.request.httpMethod == HttpMethod.Post) {
                assertEquals("Body", call.receive())
                call.respond(HttpStatusCode.OK)
            }
        }
        val call = handleRequest {
            method = HttpMethod.Post
            setBody("Body")
        }
        assertTrue(call.response.status()!!.isSuccess())
    }

    @Test
    fun `application with handler that returns true on POST method`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            if (call.request.httpMethod == HttpMethod.Post) {
                call.respond(HttpStatusCode.OK)
            }
        }
        on("making a GET request") {
            val call = handleRequest { method = HttpMethod.Get }
            it("should not be handled") {
                assertFalse(call.response.status()!!.isSuccess())
            }
        }
        on("making a POST request") {
            val call = handleRequest { method = HttpMethod.Post }
            it("should be handled") {
                assertTrue(call.response.status()!!.isSuccess())
            }
        }
    }
}
