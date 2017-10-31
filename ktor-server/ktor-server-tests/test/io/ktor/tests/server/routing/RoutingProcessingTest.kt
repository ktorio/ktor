package io.ktor.tests.server.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class RoutingProcessingTest {
    @Test fun `routing on GET foo-bar`() = withTestApplication {
        application.routing {
            get("/foo/bar") {
                call.respond(HttpStatusCode.OK)
            }
        }

        on("making get request to /foo/bar") {
            val result = handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Get
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }

        on("making post request to /foo/bar") {
            val result = handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertFalse(result.requestHandled)
            }
        }
    }

    @Test fun `routing on GET user with parameter`() = withTestApplication {
        var username = ""
        application.routing {
            route("user") {
                param("name") {
                    method(HttpMethod.Get) {
                        handle {
                            username = call.parameters["name"] ?: ""
                        }
                    }
                }
            }
        }
        on("making get request to /user with query parameters") {
            handleRequest {
                uri = "/user?name=john"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }

    }

    @Test fun `routing on GET user with surrounded parameter`() = withTestApplication {
        var username = ""
        application.routing {
            get("/user-{name}-get") {
                username = call.parameters["name"] ?: ""
            }
        }
        on("making get request to /user with query parameters") {
            handleRequest {
                uri = "/user-john-get"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }

    }

    @Test fun `verify most specific selected`() = withTestApplication {
        var path = ""
        application.routing {
            get("{path...}") {
                path = call.parameters.getAll("path")?.joinToString("/") ?: "/"
            }
            get("z/{path...}") {
                path = "[Z] " + (call.parameters.getAll("path")?.joinToString("/") ?: "/")
            }
        }
        on("making get request to /z/a/b/c") {
            handleRequest {
                uri = "/z/a/b/c"
                method = HttpMethod.Get
            }
            it("should have handled by more specific rout") {
                assertEquals("[Z] a/b/c", path)
            }
        }
        on("making get request to /x/a/b/c") {
            handleRequest {
                uri = "/x/a/b/c"
                method = HttpMethod.Get
            }
            it("should have handled by more specific rout") {
                assertEquals("x/a/b/c", path)
            }
        }
    }

    @Test fun `routing on GET -user-username with interceptors`() = withTestApplication {

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var userName = ""
        var userNameGotWithinInterceptor = false

        application.routing {
            route("user") {
                intercept(ApplicationCallPipeline.Call) {
                    userIntercepted = true
                    wrappedWithInterceptor = true
                    proceed()
                    wrappedWithInterceptor = false
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                    userNameGotWithinInterceptor = wrappedWithInterceptor
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(userNameGotWithinInterceptor, "should have processed /user/username in context of interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be after`() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        application.routing {
            intercept(ApplicationCallPipeline.Call) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                proceed()
                wrappedWithInterceptor = false
            }

            route("user") {
                intercept(ApplicationCallPipeline.Infrastructure) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertFalse(routingInterceptorWrapped, "should have processed nested routing interceptor in a prior phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be before because of phase`() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        application.routing {
            intercept(ApplicationCallPipeline.Infrastructure) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                proceed()
                wrappedWithInterceptor = false
            }

            route("user") {
                intercept(ApplicationCallPipeline.Call) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be before because of order`() = withTestApplication {
        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        application.routing {
            intercept(ApplicationCallPipeline.Infrastructure) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                proceed()
                wrappedWithInterceptor = false
            }

            route("user") {
                intercept(ApplicationCallPipeline.Infrastructure) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    class Foo
    @Test fun `intercept receive pipeline`() = withTestApplication {

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var instance: Foo? = null
        var routingInterceptorWrapped = false

        application.routing {
            receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                proceed()
                wrappedWithInterceptor = false
            }

            route("user") {
                receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                    proceedWith(ApplicationReceiveRequest(it.type, Foo()))
                }
                get("{username}") {
                    instance = call.receive<Foo>()
                }
            }
        }

        on("handling GET /user/john") {
            handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertNotNull(instance)
        }

    }

    @Test fun `verify accept header processing`() = withTestApplication {
        application.routing {
            route("/") {
                accept(ContentType.Text.Plain) {
                    handle {
                        call.respond("OK")
                    }
                }
                accept(ContentType.Application.Json) {
                    handle {
                        call.respondText("{\"status\": \"OK\"}", ContentType.Application.Json)
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/plain")
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "application/json")
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("{\"status\": \"OK\"}", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/html")
        }.let { call ->
            assertFalse { call.requestHandled }
        }
    }
}
