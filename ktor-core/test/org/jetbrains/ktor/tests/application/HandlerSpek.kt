package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.spek.api.*
import kotlin.test.*

class HandlerSpek : Spek() {init {

    given("application with empty handler") {
        val testHost = createTestHost()
        on("making a request") {
            val request = testHost.getRequest { }
            it("should not be handled") {
                shouldBeFalse(request.handled)
            }
            it("should not contain response") {
                shouldBeNull(request.response)
            }
        }
    }

    given("application with transparent handler") {
        val testHost = createTestHost()
        testHost.application.intercept { request, next -> next(request) }
        on("making a request") {
            val request = testHost.getRequest { }
            it("should not be handled") {
                shouldBeFalse(request.handled)
            }
            it("should not contain response") {
                shouldBeNull(request.response)
            }
        }
    }

    given("application with handler returning true") {
        val testHost = createTestHost()
        testHost.application.intercept { request, next -> true }
        on("making a request") {
            val request = testHost.getRequest { }
            it("should be handled") {
                shouldBeTrue(request.handled)
            }
            it("should not contain response") {
                shouldBeNull(request.response)
            }
        }
    }

    given("application with handler that returns a valid response") {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            request.response {
            }
            true
        }
        on("making a request") {
            val request = testHost.getRequest { }

            it("should be handled") {
                shouldBeTrue(request.handled)
            }
            it("should contain response") {
                shouldNotBeNull(request.response)
            }
        }
    }

    given("application with handler that returns two responses") {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            request.response { }
            request.response { }
            true
        }
        on("making a request") {
            val request = fails {
                testHost.getRequest { }
            }!!
            it("should throw invalid operation") {
                shouldEqual(request.javaClass, javaClass<IllegalStateException>())
            }
        }
    }

    given("application with handler that returns true on POST method") {
        val testHost = createTestHost()
        testHost.application.intercept { request, next ->
            if (request.httpMethod == HttpMethod.Post) {
                request.response().status(HttpStatusCode.OK)
                true
            } else
                false
        }
        on("making a GET request") {
            val request = testHost.getRequest { httpMethod = HttpMethod.Get }
            it("should not be handled") {
                shouldBeFalse(request.handled)
            }
            it("should not return response") {
                shouldBeNull(request.response)
            }
        }
        on("making a POST request") {
            val request = testHost.getRequest { httpMethod = HttpMethod.Post }
            it("should be handled") {
                shouldBeTrue(request.handled)
            }
            it("should return response") {
                shouldNotBeNull(request.response)
            }
        }
    }
    /*
    TODO: This is fundamentally wrong since you shouldn't be setting ApplicationRequest "contentType" or "accept" since these are values passed in.
        given("application with handler that returns true on text/plain content type") {
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
                    shouldBeTrue(request.handled)
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
}
