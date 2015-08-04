package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class HandlerTest {

    Test fun `application with empty handler`() {
        val testHost = createTestHost()
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with transparent handler`() {
        val testHost = createTestHost()
        testHost.application.intercept { request, next -> next(request) }
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with handler returning true`() {
        val testHost = createTestHost()
        testHost.application.intercept { request, next -> ApplicationRequestStatus.Handled }
        on("making a request") {
            val request = testHost.handleRequest { }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with handler that returns a valid response`() {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            request.respond {
                send()
            }
            ApplicationRequestStatus.Handled
        }
        on("making a request") {
            val request = testHost.handleRequest { }

            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should contain response") {
                assertNotNull(request.response)
            }
        }
    }

    Test fun `application with handler that returns two responses`() {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            request.respond { send() }
            request.respond { send() }
            ApplicationRequestStatus.Handled
        }
        on("making a request") {
            val request = fails {
                testHost.handleRequest { }
            }!!
            it("should throw invalid operation") {
                assertEquals(request.javaClass, javaClass<IllegalStateException>())
            }
        }
    }

    Test fun `application with handler that returns true on POST method`() {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            if (request.method == HttpMethod.Post) {
                request.respond {
                    status(HttpStatusCode.OK)
                    send()
                }
                ApplicationRequestStatus.Handled
            } else
                ApplicationRequestStatus.Unhandled
        }
        on("making a GET request") {
            val request = testHost.handleRequest { method = HttpMethod.Get }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not return response") {
                assertNull(request.response)
            }
        }
        on("making a POST request") {
            val request = testHost.handleRequest { method = HttpMethod.Post }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should return response") {
                assertNotNull(request.response)
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

