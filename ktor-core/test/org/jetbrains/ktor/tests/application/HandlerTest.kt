package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class HandlerTest {

    @Test fun `application with empty handler`() {
        val testHost = createTestHost()
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationCallResult.Unhandled, request.requestResult)
            }
        }
    }

    @Test fun `application with transparent handler`() {
        val testHost = createTestHost()
        testHost.application.intercept { call ->
        }
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationCallResult.Unhandled, request.requestResult)
            }
        }
    }

    @Test fun `application with handler returning true`() {
        val testHost = createTestHost()
        testHost.application.intercept { call -> call.respond(HttpStatusCode.OK) }
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should be handled") {
                assertEquals(ApplicationCallResult.Handled, request.requestResult)
            }
        }
    }

    @Test fun `application with handler that checks body on POST method`() = withTestApplication {
        application.intercept { call ->
            if (call.request.httpMethod == HttpMethod.Post) {
                assertEquals(call.request.content.get<String>(), "Body")
                call.respond(HttpStatusCode.OK)
            }
        }
        val result = handleRequest {
            method = HttpMethod.Post
            body = "Body"
        }
        assertEquals(ApplicationCallResult.Handled, result.requestResult)
    }

    @Test fun `application with handler that returns true on POST method`() = withTestApplication {
        application.intercept { call ->
            if (call.request.httpMethod == HttpMethod.Post) {
                call.respond(HttpStatusCode.OK)
            }
        }
        on("making a GET request") {
            val request = handleRequest { method = HttpMethod.Get }
            it("should not be handled") {
                assertEquals(ApplicationCallResult.Unhandled, request.requestResult)
            }
        }
        on("making a POST request") {
            val request = handleRequest { method = HttpMethod.Post }
            it("should be handled") {
                assertEquals(ApplicationCallResult.Handled, request.requestResult)
            }
        }
    }

    @Test fun `application with handler that intercepts creation of headers`() = withTestApplication {
        application.intercept { call ->
            call.response.headers.intercept { name, value, header ->
                if (name == "Content-Type" && value == "text/plain")
                    header(name, "text/xml")
                else
                    header(name, value)
            }
        }

        on("asking for a response") {
            application.intercept { call ->
                call.response.contentType(ContentType.Text.Plain)

            }
            handleRequest { method = HttpMethod.Get }.let {
                it("should be unhandled") {
                    assertEquals(ApplicationCallResult.Unhandled, it.requestResult)
                }
                it("should have modified content type to text/xml") {
                    assertEquals("text/xml", it.response.headers["Content-Type"])
                }
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

