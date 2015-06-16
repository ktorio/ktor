package ktor.tests.routing

import ktor.http.*
import ktor.routing.*
import ktor.tests.*
import org.jetbrains.spek.api.*

class RoutingProcessingSpek : Spek() {init {
    given("host with routing on GET /foo/bar") {
        val testHost = createTestHost()
        testHost.application.routing {
            get("/foo/bar") {
                response().status(HttpStatusCode.OK)
            }
        }

        on("making get request to /foo/bar") {
            val result = testHost.getRequest {
                uri = "/foo/bar"
                httpMethod = HttpMethod.Get
            }
            it("should be handled") {
                shouldBeTrue(result.handled)
            }
            it("should have a response") {
                shouldNotBeNull(result.response)
            }
            it("should have a response with OK status") {
                shouldEqual(HttpStatusCode.OK.value, result.response!!.status)
            }
        }

        on("making post request to /foo/bar") {
            val result = testHost.getRequest {
                uri = "/foo/bar"
                httpMethod = HttpMethod.Post
            }
            it("should not be handled") {
                shouldBeFalse(result.handled)
            }
            it("should have no response") {
                shouldBeNull(result.response)
            }
        }
    }

    given("host with routing on GET /user with parameter") {
        val testHost = createTestHost()
        var username = listOf<String>()
        testHost.application.routing {
            location("user") {
                param("name") {
                    get {
                        handle {
                            username = parameters["name"] ?: listOf()
                        }
                    }
                }
            }
        }
        on("making get request to /user with query parameters") {
            testHost.getRequest {
                uri = "/user?name=john"
                httpMethod = HttpMethod.Get
            }
            it("should have processed username once") {
                shouldEqual(1, username.size())
            }
            it("should have extracted username") {
                shouldEqual("john", username.first())
            }
        }

    }

    given("host with routing on GET /user/username with interceptors") {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var userName = ""
        var userNameGotWithinInterceptor = false

        testHost.application.routing {
            location("user") {
                intercept { request, next ->
                    userIntercepted = true
                    try {
                        wrappedWithInterceptor = true
                        next(request)
                    } finally {
                        wrappedWithInterceptor = false
                    }
                }
                get(":username") {
                    userName = parameters["username"]?.first() ?: ""
                    userNameGotWithinInterceptor = wrappedWithInterceptor
                }
            }
        }

        on("handling GET /user/john") {
            testHost.getRequest {
                uri = "/user/john"
                httpMethod = HttpMethod.Get
            }
            it("should have processed interceptor on /user node") {
                shouldBeTrue(userIntercepted)
            }
            it("should have processed get handler on /user/username node") {
                shouldEqual(userName, "john")
            }
            it("should have processed /user/username in context of interceptor") {
                shouldBeTrue(userNameGotWithinInterceptor)
            }
        }
    }
}
}
