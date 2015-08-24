package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class RoutingProcessingTest {
    Test fun `host with routing on GET foo-bar`() {
        val testHost = createTestHost()
        testHost.application.routing {
            get("/foo/bar") {
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }

        on("making get request to /foo/bar") {
            val result = testHost.handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Get
            }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK.value, result.response.status())
            }
        }

        on("making post request to /foo/bar") {
            val result = testHost.handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, result.requestResult)
            }
        }
    }

    Test fun `host with routing on GET user with parameter`() {
        val testHost = createTestHost()
        var username = listOf<String>()
        testHost.application.routing {
            route("user") {
                param("name") {
                    method(HttpMethod.Get) {
                        handle {
                            username = parameters["name"] ?: listOf()
                            ApplicationRequestStatus.Handled
                        }
                    }
                }
            }
        }
        on("making get request to /user with query parameters") {
            testHost.handleRequest {
                uri = "/user?name=john"
                method = HttpMethod.Get
            }
            it("should have processed username once") {
                assertEquals(1, username.size())
            }
            it("should have extracted username") {
                assertEquals("john", username.single())
            }
        }

    }

    Test fun `host with routing on GET -user-username with interceptors`() {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var userName = ""
        var userNameGotWithinInterceptor = false

        testHost.application.routing {
            route("user") {
                addInterceptor { request, next ->
                    userIntercepted = true
                    try {
                        wrappedWithInterceptor = true
                        next(request)
                    } finally {
                        wrappedWithInterceptor = false
                    }
                }
                get("{username}") {
                    userName = parameters["username"]?.first() ?: ""
                    userNameGotWithinInterceptor = wrappedWithInterceptor
                    ApplicationRequestStatus.Handled
                }
            }
        }

        on("handling GET /user/john") {
            testHost.handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            it("should have processed interceptor on /user node") {
                assertTrue(userIntercepted)
            }
            it("should have processed get handler on /user/username node") {
                assertEquals(userName, "john")
            }
            it("should have processed /user/username in context of interceptor") {
                assertTrue(userNameGotWithinInterceptor)
            }
        }
    }
}
