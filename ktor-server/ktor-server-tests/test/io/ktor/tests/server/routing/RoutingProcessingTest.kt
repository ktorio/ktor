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
                intercept(ApplicationCallPipeline.Features) {
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
            intercept(ApplicationCallPipeline.Features) {
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
            intercept(ApplicationCallPipeline.Features) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                proceed()
                wrappedWithInterceptor = false
            }

            route("user") {
                intercept(ApplicationCallPipeline.Features) {
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

    @Test
    fun `routing with tracing`() = withTestApplication {
        var trace: RoutingResolveTrace? = null
        application.routing {
            trace {
                trace = it
            }
            get("/bar") { call.respond("/bar") }
            get("/baz") { call.respond("/baz") }
            get("/baz/x") { call.respond("/baz/x") }
            get("/baz/x/{optional?}") { call.respond("/baz/x/{optional?}") }
            get("/baz/{y}") { call.respond("/baz/{y}") }
            get("/baz/{y}/value") { call.respond("/baz/{y}/value") }
            get("/{param}") { call.respond("/{param}") }
            get("/{param}/x") { call.respond("/{param}/x") }
            get("/{param}/x/z") { call.respond("/{param}/x/z") }
            get("/*/extra") { call.respond("/*/extra") }

        }

        handleRequest {
            uri = "/bar"
            method = HttpMethod.Get
        }.let {
            assertEquals("/bar", it.response.content)
            assertEquals("""Trace for [bar]
/, segment:0 -> SUCCESS @ /bar/(method:GET))
  /bar, segment:1 -> SUCCESS @ /bar/(method:GET))
    /bar/(method:GET), segment:1 -> SUCCESS @ /bar/(method:GET))
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz)
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param})
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/bar/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}/x", it.response.content)
            assertEquals("""Trace for [bar, x]
/, segment:0 -> SUCCESS; Parameters [param=[bar]] @ /{param}/x/(method:GET))
  /bar, segment:1 -> FAILURE "Not all segments matched" @ /bar/(method:GET))
    /bar/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /bar/(method:GET))
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz)
  /{param}, segment:1 -> SUCCESS @ /{param}/x/(method:GET))
    /{param}/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /{param}/(method:GET))
    /{param}/x, segment:2 -> SUCCESS @ /{param}/x/(method:GET))
      /{param}/x/(method:GET), segment:2 -> SUCCESS @ /{param}/x/(method:GET))
      /{param}/x/z, segment:2 -> FAILURE "Selector didn't match" @ /{param}/x/z)
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/baz/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x", it.response.content)
            assertEquals("""Trace for [baz, x]
/, segment:0 -> SUCCESS @ /baz/x/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:1 -> SUCCESS @ /baz/x/(method:GET))
    /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET))
    /baz/x, segment:2 -> SUCCESS @ /baz/x/(method:GET))
      /baz/x/(method:GET), segment:2 -> SUCCESS @ /baz/x/(method:GET))
      /baz/x/{optional?}, segment:2 -> FAILURE "Better match was already found" @ /baz/x/{optional?})
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y})
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param})
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/baz/doo"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/{y}", it.response.content)
            assertEquals("""Trace for [baz, doo]
/, segment:0 -> SUCCESS; Parameters [y=[doo]] @ /baz/{y}/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:1 -> SUCCESS; Parameters [y=[doo]] @ /baz/{y}/(method:GET))
    /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET))
    /baz/x, segment:1 -> FAILURE "Selector didn't match" @ /baz/x)
    /baz/{y}, segment:2 -> SUCCESS @ /baz/{y}/(method:GET))
      /baz/{y}/(method:GET), segment:2 -> SUCCESS @ /baz/{y}/(method:GET))
      /baz/{y}/value, segment:2 -> FAILURE "Selector didn't match" @ /baz/{y}/value)
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param})
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/baz/x/z"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x/{optional?}", it.response.content)
            assertEquals("""Trace for [baz, x, z]
/, segment:0 -> SUCCESS; Parameters [optional=[z]] @ /baz/x/{optional?}/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:1 -> SUCCESS; Parameters [optional=[z]] @ /baz/x/{optional?}/(method:GET))
    /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET))
    /baz/x, segment:2 -> SUCCESS; Parameters [optional=[z]] @ /baz/x/{optional?}/(method:GET))
      /baz/x/(method:GET), segment:2 -> FAILURE "Not all segments matched" @ /baz/x/(method:GET))
      /baz/x/{optional?}, segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET))
        /baz/x/{optional?}/(method:GET), segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET))
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y})
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param})
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/baz/x/value"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x/{optional?}", it.response.content)
            assertEquals("""Trace for [baz, x, value]
/, segment:0 -> SUCCESS; Parameters [optional=[value]] @ /baz/x/{optional?}/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:1 -> SUCCESS; Parameters [optional=[value]] @ /baz/x/{optional?}/(method:GET))
    /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET))
    /baz/x, segment:2 -> SUCCESS; Parameters [optional=[value]] @ /baz/x/{optional?}/(method:GET))
      /baz/x/(method:GET), segment:2 -> FAILURE "Not all segments matched" @ /baz/x/(method:GET))
      /baz/x/{optional?}, segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET))
        /baz/x/{optional?}/(method:GET), segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET))
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y})
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param})
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/p"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}", it.response.content)
            assertEquals("""Trace for [p]
/, segment:0 -> SUCCESS; Parameters [param=[p]] @ /{param}/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz)
  /{param}, segment:1 -> SUCCESS @ /{param}/(method:GET))
    /{param}/(method:GET), segment:1 -> SUCCESS @ /{param}/(method:GET))
    /{param}/x, segment:1 -> FAILURE "Selector didn't match" @ /{param}/x)
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }

        handleRequest {
            uri = "/p/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}/x", it.response.content)
            assertEquals("""Trace for [p, x]
/, segment:0 -> SUCCESS; Parameters [param=[p]] @ /{param}/x/(method:GET))
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar)
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz)
  /{param}, segment:1 -> SUCCESS @ /{param}/x/(method:GET))
    /{param}/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /{param}/(method:GET))
    /{param}/x, segment:2 -> SUCCESS @ /{param}/x/(method:GET))
      /{param}/x/(method:GET), segment:2 -> SUCCESS @ /{param}/x/(method:GET))
      /{param}/x/z, segment:2 -> FAILURE "Selector didn't match" @ /{param}/x/z)
  /*, segment:0 -> FAILURE "Better match was already found" @ /*)
""".toPlatformLineSeparators(), trace?.buildText())
        }
    }

    private fun String.toPlatformLineSeparators() = lines().joinToString(System.lineSeparator())
}
