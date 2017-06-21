package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class HandlerTest {

    @Test fun `application with empty handler`() = withTestApplication {
        on("making a request") {
            val request = handleRequest { }
            it("should not be handled") {
                assertFalse(request.requestHandled)
            }
        }
    }

    @Test fun `application with transparent handler`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {}
        on("making a request") {
            val request = handleRequest { }
            it("should not be handled") {
                assertFalse(request.requestHandled)
            }
        }
    }

    @Test fun `application with handler returning true`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) { call.respond(HttpStatusCode.OK) }
        on("making a request") {
            val request = handleRequest { }
            it("should be handled") {
                assertTrue(request.requestHandled)
            }
        }
    }

    @Test fun `application with handler that checks body on POST method`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            if (call.request.httpMethod == HttpMethod.Post) {
                assertEquals(call.request.receive<String>(), "Body")
                call.respond(HttpStatusCode.OK)
            }
        }
        val result = handleRequest {
            method = HttpMethod.Post
            body = "Body"
        }
        assertTrue(result.requestHandled)
    }

    @Test fun `application with handler that returns true on POST method`() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            if (call.request.httpMethod == HttpMethod.Post) {
                call.respond(HttpStatusCode.OK)
            }
        }
        on("making a GET request") {
            val request = handleRequest { method = HttpMethod.Get }
            it("should not be handled") {
                assertFalse(request.requestHandled)
            }
        }
        on("making a POST request") {
            val request = handleRequest { method = HttpMethod.Post }
            it("should be handled") {
                assertTrue(request.requestHandled)
            }
        }
    }

    /*
    TODO: This is fundamentally wrong since you shouldn't be setting ApplicationRequest "contentType" or "accept" since these are values passed in.
        Test fun `application with handler that returns true on text/plain content type`() {
            val testHost = createTestHost()
            testHost.application.intercept { request, next ->
                if (request.contentType() == ContentType.Text.Plain ) {
                    request.response().status(HttpStatusCode.OK)
                    true
                }
                else
                    false
            }
            on("making a request for content type text/plain") {
                val request = testHost.makeRequest { contentType = ContentType.Text.Plain }
                it("should be handled") {
                    assertTrue(request.handled)
                }
                it("should return response") {
                    shouldNotBeNull(request.response)
                }
            }
            on("making a request for content type any") {
                val request = testHost.makeRequest { contentType = ContentType.Any }
                it("should not be handled") {
                    shouldBeFalse(request.handled)
                }
                it("should not return response") {
                    shouldBeNull(request.response)
                }
            }
        }
    */
}

