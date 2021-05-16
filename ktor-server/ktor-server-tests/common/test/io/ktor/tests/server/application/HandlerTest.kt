/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

class HandlerTest {

    @Test
    fun emptyHandler() = testSuspend {
        withTestApplication {
            on("making a request") {
                val call = handleRequest { }
                it("should not be handled") {
                    assertFalse(call.requestHandled)
                }
            }
        }
    }

    @Test
    fun transparentHandler() = testSuspend {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {}
            on("making a request") {
                val call = handleRequest { }
                it("should not be handled") {
                    assertFalse(call.requestHandled)
                }
            }
        }
    }

    @Test
    fun handlerReturningTrue() = testSuspend {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call.respond(HttpStatusCode.OK) }
            on("making a request") {
                val call = handleRequest { }
                it("should be handled") {
                    assertTrue(call.requestHandled)
                }
            }
        }
    }

    @Test
    fun handlerThatChecksBodyOnPOST() = testSuspend {
        withTestApplication {
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
            assertTrue(call.requestHandled)
        }
    }

    @Test
    fun handlerThatTeturnsTrueOnPOST() = testSuspend {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                if (call.request.httpMethod == HttpMethod.Post) {
                    call.respond(HttpStatusCode.OK)
                }
            }
            on("making a GET request") {
                val call = handleRequest { method = HttpMethod.Get }
                it("should not be handled") {
                    assertFalse(call.requestHandled)
                }
            }
            on("making a POST request") {
                val call = handleRequest { method = HttpMethod.Post }
                it("should be handled") {
                    assertTrue(call.requestHandled)
                }
            }
        }
    }
}
