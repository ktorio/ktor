/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

private enum class SelectedRoute { Get, Param, Header, None }
private class Foo

class RoutingProcessingTest {

    @Test
    fun testRoutingOnGETFooBar() = withTestApplication {
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
                assertFalse(result.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun testRoutingOnGETUserWithParameter() = withTestApplication {
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

    @Test
    fun testRoutingOnGETUserWithSurroundedParameter() = withTestApplication {
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

    @Test
    fun testRoutingOnParamRoute() = withTestApplication {
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("test") {
                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                get {
                    selectedRoute = SelectedRoute.Get
                }
            }
        }
        on("making get request to /test with `param` query parameter") {
            handleRequest {
                uri = "/test?param=value"
                method = HttpMethod.Get
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making get request to /test without `param` query parameter") {
            handleRequest {
                uri = "/test"
                method = HttpMethod.Get
            }
            it("should choose get routing") {
                assertEquals(selectedRoute, SelectedRoute.Get)
            }
        }
    }

    @Test
    fun testRoutingOnOptionalParamRoute() = withTestApplication {
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("test") {
                optionalParam("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                get {
                    selectedRoute = SelectedRoute.Get
                }
            }
        }
        on("making get request to /test with `param` query parameter") {
            handleRequest {
                uri = "/test?param=value"
                method = HttpMethod.Get
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making get request to /test without `param` query parameter") {
            handleRequest {
                uri = "/test"
                method = HttpMethod.Get
            }
            it("should choose get routing") {
                assertEquals(selectedRoute, SelectedRoute.Get)
            }
        }
    }

    @Test
    fun testRoutingOnRoutesWithSameQualityShouldBeBasedOnOrder() = withTestApplication {
        // `accept {}` and `param {}` use quality = 1.0
        var selectedRoute = SelectedRoute.None
        application.routing {
            route("paramFirst") {
                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }

                accept(ContentType.Text.Plain) {
                    handle {
                        selectedRoute = SelectedRoute.Header
                    }
                }
            }
            route("headerFirst") {
                accept(ContentType.Text.Plain) {
                    handle {
                        selectedRoute = SelectedRoute.Header
                    }
                }

                param("param") {
                    handle {
                        selectedRoute = SelectedRoute.Param
                    }
                }
            }
        }
        on("making request to /paramFirst with `param` query parameter and accept header") {
            handleRequest {
                uri = "/paramFirst?param=value"
                addHeader(HttpHeaders.Accept, "text/plain")
            }
            it("should choose param routing") {
                assertEquals(selectedRoute, SelectedRoute.Param)
            }
        }
        on("making request to /headerFirst with `param` query parameter and accept header") {
            handleRequest {
                uri = "/headerFirst?param=value"
                addHeader(HttpHeaders.Accept, "text/plain")
            }
            it("should choose header routing") {
                assertEquals(selectedRoute, SelectedRoute.Header)
            }
        }
    }

    @Test
    fun testMostSpecificSelected() = withTestApplication {
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
            it("should have handled by more specific route") {
                assertEquals("[Z] a/b/c", path)
            }
        }
        on("making get request to /x/a/b/c") {
            handleRequest {
                uri = "/x/a/b/c"
                method = HttpMethod.Get
            }
            it("should have handled by more specific route") {
                assertEquals("x/a/b/c", path)
            }
        }
    }

    @Test
    fun testRoutingOnGETUserUsernameWithInterceptors() = withTestApplication {
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

    @Test
    fun testRouteWithTypedBody(): Unit = withTestApplication {
        application.routing {
            post<String> { answer ->
                assertEquals("42", answer)
            }
            put<String>("/put") { answer ->
                assertEquals("42", answer)
            }
            patch<String>("/patching") { answer ->
                assertEquals("42", answer)
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            setBody("42")
        }
        handleRequest(HttpMethod.Put, "/put") {
            setBody("42")
        }
        handleRequest(HttpMethod.Patch, "/patching") {
            setBody("42")
        }
    }

    @Test
    fun testInterceptionOrderWhenOuterShouldBeAfter() = withTestApplication {
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

    @Test
    fun testInterceptionOrderWhenOuterShouldBeBeforeBecauseOfPhase() = withTestApplication {
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

    @Test
    fun testInterceptionOrderWhenOuterShouldBeBeforeBecauseOfOrder() = withTestApplication {
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

    @Test
    fun testInterceptReceivePipeline() = withTestApplication {
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

    @Test
    fun testAcceptHeaderProcessing() = withTestApplication {
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
            assertEquals("OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "application/json")
        }.let { call ->
            assertEquals("{\"status\": \"OK\"}", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
        }.let { call ->
            assertEquals("OK", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/html")
        }.let { call ->
            assertFalse(call.response.status()!!.isSuccess())
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "...lla..laa..la")
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    fun testTransparentSelectorWithHandler() = withTestApplication {
        application.routing {
            route("") {
                transparent {
                    handle { call.respond("OK") }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("OK", call.response.content)
        }
    }

    @Test
    fun testTransparentSelectorPriority() = withTestApplication {
        application.routing {
            route("root") {
                optionalParam("param") {
                    handle {
                        call.respond("param")
                    }
                }
                transparent {
                    get {
                        call.respond("get")
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/root?param=123").let { call ->
            assertEquals("param", call.response.content)
        }
        handleRequest(HttpMethod.Get, "/root").let { call ->
            assertEquals("get", call.response.content)
        }
    }

    @Test
    fun testHostAndPortRoutingProcessing(): Unit = withTestApplication {
        application.routing {
            route("/") {
                host("my-host", 8080) {
                    get("1") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                host("(www\\.)?my-host.net".toRegex()) {
                    get("3") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                host(listOf("www.my-host.net", "my-host.net")) {
                    get("4") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
                port(9090) {
                    get("2") {
                        call.respond(
                            "host = ${call.parameters[HostRouteSelector.HostNameParameter]}, " +
                                "port = ${call.parameters[HostRouteSelector.PortParameter]}"
                        )
                    }
                }
            }
        }

        handleRequest {
            uri = "/1"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host:8080")
        }.let { call ->
            assertEquals("host = my-host, port = 8080", call.response.content)
        }

        handleRequest {
            uri = "/1"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host:80")
        }.let { call ->
            assertEquals(null, call.response.content)
        }

        handleRequest {
            uri = "/2"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host2:9090")
        }.let { call ->
            assertEquals("host = my-host2, port = 9090", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host.net:9091")
        }.let { call ->
            assertEquals("host = my-host.net, port = 9091", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "www.my-host.net:9092")
        }.let { call ->
            assertEquals("host = www.my-host.net, port = 9092", call.response.content)
        }

        handleRequest {
            uri = "/3"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "sub.my-host.net:9092")
        }.let { call ->
            assertEquals(null, call.response.content)
        }

        handleRequest {
            uri = "/4"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "my-host.net:9093")
        }.let { call ->
            assertEquals("host = my-host.net, port = 9093", call.response.content)
        }

        handleRequest {
            uri = "/4"
            method = HttpMethod.Get
            addHeader(HttpHeaders.Host, "www.my-host.net:9094")
        }.let { call ->
            assertEquals("host = www.my-host.net, port = 9094", call.response.content)
        }
    }

    @Test
    fun testLocalPortRouteProcessing(): Unit = withTestApplication {
        application.routing {
            route("/") {
                // TestApplicationRequest.local defaults to 80 in the absence of headers
                // so connections paths to port 80 in tests should work, whereas other ports shouldn't
                localPort(80) {
                    get("http") {
                        call.respond("received")
                    }
                }
                localPort(443) {
                    get("https") {
                        fail("shouldn't be received")
                    }
                }
            }
        }

        // accepts calls to the specified port
        handleRequest(HttpMethod.Get, "/http").apply {
            assertEquals("received", response.content)
        }

        // ignores calls to different ports
        handleRequest(HttpMethod.Get, "/https").apply {
            assertNull(response.content)
        }

        // I tried to write a test to confirm that it ignores the HTTP Host header,
        // but I couldn't get it to work without adding headers, because
        // [io.ktor.server.testing.TestApplicationRequest.local] is hard-coded to
        // extract the value of those headers.
        // (even though, according to docs, it shouldn't; this should be done by `origin`)

        // I also tried to create a test listening to multiple ports, but I couldn't get it
        // to work because of the same reason above.
    }

    @Test
    fun testRoutingWithTracing() = withTestApplication {
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
            header("a", "x") { get { call.respond("a") } }
            header("b", "x") { get { call.respond("b") } }
        }

        handleRequest {
            uri = "/bar"
            method = HttpMethod.Get
        }.let {
            assertEquals("/bar", it.response.content)
            assertEquals(
                """Trace for [bar]
/, segment:0 -> SUCCESS @ /
  /bar, segment:1 -> SUCCESS @ /bar
    /bar/(method:GET), segment:1 -> SUCCESS @ /bar/(method:GET)
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param}
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "bar" -> "(method:GET)"
Route resolve result:
  SUCCESS @ /bar/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/bar/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}/x", it.response.content)
            assertEquals(
                """Trace for [bar, x]
/, segment:0 -> SUCCESS @ /
  /bar, segment:1 -> SUCCESS @ /bar
    /bar/(method:GET), segment:1 -> SUCCESS @ /bar/(method:GET)
      /bar/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /bar/(method:GET)
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz
  /{param}, segment:1 -> SUCCESS; Parameters [param=[bar]] @ /{param}
    /{param}/(method:GET), segment:1 -> SUCCESS @ /{param}/(method:GET)
      /{param}/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /{param}/(method:GET)
    /{param}/x, segment:2 -> SUCCESS @ /{param}/x
      /{param}/x/(method:GET), segment:2 -> SUCCESS @ /{param}/x/(method:GET)
      /{param}/x/z, segment:2 -> FAILURE "Selector didn't match" @ /{param}/x/z
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "{param}" -> "x" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [param=[bar]] @ /{param}/x/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/baz/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x", it.response.content)
            assertEquals(
                """Trace for [baz, x]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:1 -> SUCCESS @ /baz
    /baz/(method:GET), segment:1 -> SUCCESS @ /baz/(method:GET)
      /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET)
    /baz/x, segment:2 -> SUCCESS @ /baz/x
      /baz/x/(method:GET), segment:2 -> SUCCESS @ /baz/x/(method:GET)
      /baz/x/{optional?}, segment:2 -> FAILURE "Better match was already found" @ /baz/x/{optional?}
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y}
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param}
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "baz" -> "x" -> "(method:GET)"
Route resolve result:
  SUCCESS @ /baz/x/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/baz/doo"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/{y}", it.response.content)
            assertEquals(
                """Trace for [baz, doo]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:1 -> SUCCESS @ /baz
    /baz/(method:GET), segment:1 -> SUCCESS @ /baz/(method:GET)
      /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET)
    /baz/x, segment:1 -> FAILURE "Selector didn't match" @ /baz/x
    /baz/{y}, segment:2 -> SUCCESS; Parameters [y=[doo]] @ /baz/{y}
      /baz/{y}/(method:GET), segment:2 -> SUCCESS @ /baz/{y}/(method:GET)
      /baz/{y}/value, segment:2 -> FAILURE "Selector didn't match" @ /baz/{y}/value
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param}
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "baz" -> "{y}" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [y=[doo]] @ /baz/{y}/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/baz/x/z"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x/{optional?}", it.response.content)
            assertEquals(
                """Trace for [baz, x, z]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:1 -> SUCCESS @ /baz
    /baz/(method:GET), segment:1 -> SUCCESS @ /baz/(method:GET)
      /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET)
    /baz/x, segment:2 -> SUCCESS @ /baz/x
      /baz/x/(method:GET), segment:2 -> SUCCESS @ /baz/x/(method:GET)
        /baz/x/(method:GET), segment:2 -> FAILURE "Not all segments matched" @ /baz/x/(method:GET)
      /baz/x/{optional?}, segment:3 -> SUCCESS; Parameters [optional=[z]] @ /baz/x/{optional?}
        /baz/x/{optional?}/(method:GET), segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET)
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y}
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param}
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "baz" -> "x" -> "{optional?}" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [optional=[z]] @ /baz/x/{optional?}/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/baz/x/value"
            method = HttpMethod.Get
        }.let {
            assertEquals("/baz/x/{optional?}", it.response.content)
            assertEquals(
                """Trace for [baz, x, value]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:1 -> SUCCESS @ /baz
    /baz/(method:GET), segment:1 -> SUCCESS @ /baz/(method:GET)
      /baz/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /baz/(method:GET)
    /baz/x, segment:2 -> SUCCESS @ /baz/x
      /baz/x/(method:GET), segment:2 -> SUCCESS @ /baz/x/(method:GET)
        /baz/x/(method:GET), segment:2 -> FAILURE "Not all segments matched" @ /baz/x/(method:GET)
      /baz/x/{optional?}, segment:3 -> SUCCESS; Parameters [optional=[value]] @ /baz/x/{optional?}
        /baz/x/{optional?}/(method:GET), segment:3 -> SUCCESS @ /baz/x/{optional?}/(method:GET)
    /baz/{y}, segment:1 -> FAILURE "Better match was already found" @ /baz/{y}
  /{param}, segment:0 -> FAILURE "Better match was already found" @ /{param}
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "baz" -> "x" -> "{optional?}" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [optional=[value]] @ /baz/x/{optional?}/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/p"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}", it.response.content)
            assertEquals(
                """Trace for [p]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz
  /{param}, segment:1 -> SUCCESS; Parameters [param=[p]] @ /{param}
    /{param}/(method:GET), segment:1 -> SUCCESS @ /{param}/(method:GET)
    /{param}/x, segment:1 -> FAILURE "Selector didn't match" @ /{param}/x
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "{param}" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [param=[p]] @ /{param}/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/p/x"
            method = HttpMethod.Get
        }.let {
            assertEquals("/{param}/x", it.response.content)
            assertEquals(
                """Trace for [p, x]
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz
  /{param}, segment:1 -> SUCCESS; Parameters [param=[p]] @ /{param}
    /{param}/(method:GET), segment:1 -> SUCCESS @ /{param}/(method:GET)
      /{param}/(method:GET), segment:1 -> FAILURE "Not all segments matched" @ /{param}/(method:GET)
    /{param}/x, segment:2 -> SUCCESS @ /{param}/x
      /{param}/x/(method:GET), segment:2 -> SUCCESS @ /{param}/x/(method:GET)
      /{param}/x/z, segment:2 -> FAILURE "Selector didn't match" @ /{param}/x/z
  /*, segment:0 -> FAILURE "Better match was already found" @ /*
  /(header:a = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:a = x)
  /(header:b = x), segment:0 -> FAILURE "Selector didn't match" @ /(header:b = x)
Matched routes:
  "" -> "{param}" -> "x" -> "(method:GET)"
Route resolve result:
  SUCCESS; Parameters [param=[p]] @ /{param}/x/(method:GET)
""",
                trace?.buildText()
            )
        }

        handleRequest {
            uri = "/"
            addHeader("a", "x")
            addHeader("b", "x")
            method = HttpMethod.Get
        }.let {
            assertEquals("a", it.response.content)
            assertEquals(
                """Trace for []
/, segment:0 -> SUCCESS @ /
  /bar, segment:0 -> FAILURE "Selector didn't match" @ /bar
  /baz, segment:0 -> FAILURE "Selector didn't match" @ /baz
  /{param}, segment:0 -> FAILURE "Selector didn't match" @ /{param}
  /*, segment:0 -> FAILURE "Selector didn't match" @ /*
  /(header:a = x), segment:0 -> SUCCESS @ /(header:a = x)
    /(header:a = x)/(method:GET), segment:0 -> SUCCESS @ /(header:a = x)/(method:GET)
  /(header:b = x), segment:0 -> SUCCESS @ /(header:b = x)
    /(header:b = x)/(method:GET), segment:0 -> SUCCESS @ /(header:b = x)/(method:GET)
Matched routes:
  "" -> "(header:a = x)" -> "(method:GET)"
  "" -> "(header:b = x)" -> "(method:GET)"
Route resolve result:
  SUCCESS @ /(header:a = x)/(method:GET)
""",
                trace?.buildText()
            )
        }
    }

    @Test
    fun testRouteWithParameterPrefixAndSuffixHasMorePriority() = withTestApplication {
        application.routing {
            get("/foo:{baz}") {
                call.respondText("foo")
            }
            get("/{baz}") {
                call.respondText("baz")
            }
            get("/{baz}:bar") {
                call.respondText("bar")
            }
        }

        handleRequest(HttpMethod.Get, "/foo:bar").let { call ->
            assertEquals(call.response.content, "foo")
        }

        handleRequest(HttpMethod.Get, "/baz").let { call ->
            assertEquals(call.response.content, "baz")
        }

        handleRequest(HttpMethod.Get, "/baz:bar").let { call ->
            assertEquals(call.response.content, "bar")
        }
    }

    @Test
    fun testDeepChildComparison() = withTestApplication {
        application.routing {
            header("a", "a") {
                optionalParam("a") {
                    handle {
                        call.respond("a")
                    }
                }
            }
            header("b", "b") {
                param("b") {
                    handle {
                        call.respond("b")
                    }
                }
            }
        }

        // only a match
        handleRequest(HttpMethod.Get, "/") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }

        // only a match
        handleRequest(HttpMethod.Get, "/?a=a") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }

        // both match, b has higher quality
        handleRequest(HttpMethod.Get, "/?b=b") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("b", call.response.content)
        }

        // both match, same quality
        handleRequest(HttpMethod.Get, "/?a=a&b=b") {
            addHeader("a", "a")
            addHeader("b", "b")
        }.let { call ->
            assertEquals("a", call.response.content)
        }
    }

    private fun String.toPlatformLineSeparators() = lines().joinToString(System.lineSeparator())

    private fun Route.transparent(build: Route.() -> Unit): Route {
        val route = createChild(
            object : RouteSelector() {
                override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
                    return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityTransparent)
                }
            }
        )
        route.build()
        return route
    }
}
