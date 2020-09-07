/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

fun routing(rootPath: String = "") = Route(parent = null, selector = RootRouteSelector(rootPath))
fun resolve(routing: Route, path: String, parameters: Parameters = Parameters.Empty, headers: Headers = Headers.Empty): RoutingResolveResult {
    return withTestApplication {
        RoutingResolveContext(routing, TestApplicationCall(application, coroutineContext = coroutineContext).apply {
            request.method = HttpMethod.Get
            request.uri = path + buildString {
                if (!parameters.isEmpty()) {
                    append("?")
                    parameters.formUrlEncodeTo(this)
                }
            }
            headers.flattenForEach { name, value -> request.addHeader(name, value) }
        }, emptyList()).resolve()
    }
}

fun Route.handle(selector: RouteSelector) = createChild(selector).apply { handle {} }

class RoutingResolveTest {
    @Test
    fun testEmptyRouting() {
        val root = routing()
        val result = resolve(root, "/foo/bar")
        assertTrue(result is RoutingResolveResult.Failure)
        assertEquals(root, result.route)
    }

    @Test
    fun testCustomRootPath() {
        val root = routing("context/path")
        root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))

        on("resolving /") {
            val result = resolve(root, "/")
            it("shouldn't succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
        }
        on("resolving /other/path") {
            val result = resolve(root, "/other/path")
            it("shouldn't succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
        }
        on("resolving /context/path") {
            val result = resolve(root, "/context/path")
            it("should succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
        }
        on("resolving /context/path/foo") {
            val result = resolve(root, "/context/path/foo")
            it("should succeed") {
                assertTrue(result is RoutingResolveResult.Success)
            }
        }
        on("resolving /context/path/foo/bar") {
            val result = resolve(root, "/context/path/foo/bar")
            it("shouldn't succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
        }
    }

    @Test
    fun testRoutingWithFoo() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.route)
            }
        }
        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should not succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
            it("should have fooEntry as fail entry") {
                assertEquals(fooEntry, result.route)
            }
        }
    }

    @Test
    fun testRoutingWithFooBar() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
        val barEntry = fooEntry.handle(PathSegmentConstantRouteSelector("bar", hasTrailingSlash = false))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.route)
            }
        }

        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should succeed") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should have barEntry as success entry") {
                assertEquals(barEntry, result.route)
            }
        }

        on("resolving /other/bar") {
            val result = resolve(root, "/other/bar")
            it("should not succeed") {
                assertTrue(result is RoutingResolveResult.Failure)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.route)
            }
        }
    }

    @Test
    fun testRoutingFooWithParameter() {
        val root = routing()
        val paramEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param", hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", (result as RoutingResolveResult.Success).parameters["param"])
            }
        }
    }

    @Test
    fun testRoutingFooWithSurroundedParameter() {
        val root = routing()
        val paramEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param", "a", "b", hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/avalueb")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", result.parameters["param"])
            }
        }
    }

    @Test
    fun testRoutingFooWithMultiplyParameters() {
        val root = routing()
        root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param1", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param2", hasTrailingSlash = false))

        on("resolving /foo/value1/value2") {
            val result = resolve(root, "/foo/value1/value2")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should have parameter values equal to 'value1' and 'value2'") {
                assertEquals("value1", result.parameters["param1"])
                assertEquals("value2", result.parameters["param2"])
            }
        }
    }

    @Test
    fun testRoutingFooWithMultivalueParameter() {
        val root = routing()
        root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param", hasTrailingSlash = false))
            .handle(PathSegmentParameterRouteSelector("param", hasTrailingSlash = false))

        on("resolving /foo/value1/value2") {
            val result = resolve(root, "/foo/value1/value2")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should have parameter value equal to [value1, value2]") {
                assertEquals(listOf("value1", "value2"), result.parameters.getAll("param"))
            }
        }
    }

    @Test
    fun testRoutingFooWithOptionalParameter() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentOptionalParameterRouteSelector("param", hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", result.parameters["param"])
            }
        }

        on("resolving /foo") {
            val result = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should not have parameter value") {
                assertNull(result.parameters["param"])
            }
        }
    }

    @Test
    fun testRoutingFooWithWildcard() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
        val paramEntry = fooEntry.handle(PathSegmentWildcardRouteSelector)

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
        }

        on("resolving /foo") {
            val result = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, result.route)
            }
        }
    }

    @Test
    fun testRoutingFooWithAnonymousTailcard() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentTailcardRouteSelector(hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
        }

        on("resolving /foo") {
            val result = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val result = resolve(root, "/foo/bar/baz/blah")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
        }
    }

    @Test
    fun testRoutingFooWithNamedTailcard() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
            .handle(PathSegmentTailcardRouteSelector("items", hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value") {
                assertEquals("value", result.parameters["items"])
            }
        }

        on("resolving /foo") {
            val result = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to fooEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have empty parameter") {
                assertNull(result.parameters["items"])
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val result = resolve(root, "/foo/bar/baz/blah")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("bar", "baz", "blah"), result.parameters.getAll("items"))
            }
        }
    }

    @Test
    fun testRoutingFooWithParameterEntry() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
        val paramEntry = fooEntry.handle(ParameterRouteSelector("name"))

        on("resolving /foo with query string name=value") {
            val result = resolve(root, "/foo", parametersOf("name", "value"))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("value"), result.parameters.getAll("name"))
            }
        }

        on("resolving /foo") {
            val result = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, result.route)
            }
            it("should have no parameter") {
                assertNull(result.parameters["name"])
            }
        }

        on("resolving /foo with multiple parameters") {
            val result = resolve(root, "/foo", parametersOf("name", listOf("value1", "value2")))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("value1", "value2"), result.parameters.getAll("name"))
            }
        }
    }

    @Test
    fun testRoutingFooWithQuality() {
        val root = routing()
        val fooEntry = root.createChild(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
        val paramEntry = fooEntry.handle(PathSegmentParameterRouteSelector("name", hasTrailingSlash = false))
        val constantEntry = fooEntry.handle(PathSegmentConstantRouteSelector("admin", hasTrailingSlash = false))

        on("resolving /foo/value") {
            val result = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, result.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", result.parameters["name"])
            }
        }

        on("resolving /foo/admin") {
            val result = resolve(root, "/foo/admin")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to constantEntry") {
                assertEquals(constantEntry, result.route)
            }
            it("should not have parameter value") {
                assertNull(result.parameters["name"])
            }
        }

    }

    @Test
    fun testRoutingFooWithQualityAndHeaders() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo", hasTrailingSlash = false))
        val plainEntry = fooEntry.handle(HttpHeaderRouteSelector("Accept", "text/plain"))
        val htmlEntry = fooEntry.handle(HttpHeaderRouteSelector("Accept", "text/html"))

        on("resolving /foo with more specific") {
            val result = resolve(root, "/foo", headers = headersOf("Accept", listOf("text/*, text/html, */*")))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, result.route)
            }
        }

        on("resolving /foo with equal preference") {
            val result = resolve(root, "/foo", headers = headersOf("Accept", listOf("text/plain, text/html")))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, result.route)
            }
        }

        on("resolving /foo with preference of text/plain") {
            val result = resolve(root, "/foo", headers = headersOf("Accept", listOf("text/plain, text/html; q=0.5")))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, result.route)
            }
        }

        on("resolving /foo with preference of text/html") {
            val result = resolve(root, "/foo", headers = headersOf("Accept", listOf("text/plain; q=0.5, text/html")))

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, result.route)
            }
        }
    }

    @Test
    fun testSelectMostSpecificRouteWithRoot() {
        val routing = routing()
        val rootEntry = routing.createRouteFromPath("/").apply { handle {} }
        val varargEntry = routing.createRouteFromPath("/{...}").apply { handle {} }

        on("resolving /") {
            val result = resolve(routing, "/")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to rootEntry") {
                assertEquals(rootEntry, result.route)
            }
        }
        on("resolving /path") {
            val result = resolve(routing, "/path")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to varargEntry") {
                assertEquals(varargEntry, result.route)
            }
        }

    }

    @Test
    fun testSelectMostSpecificRouteWithOptionalParam() {
        val routing = routing()
        val dateEntry = routing.createRouteFromPath("/sessions/{date}").apply { handle {} }
        val currentEntry = routing.createRouteFromPath("/sessions/current/{date?}").apply { handle {} }

        on("resolving date") {
            val result = resolve(routing, "/sessions/2017-11-02")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to rootEntry") {
                assertEquals(dateEntry, result.route)
            }
        }
        on("resolving current") {
            val result = resolve(routing, "/sessions/current/2017-11-02T10:00")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to varargEntry") {
                assertEquals(currentEntry, result.route)
            }
        }
        on("resolving optional current") {
            val result = resolve(routing, "/sessions/current")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to varargEntry") {
                assertEquals(currentEntry, result.route)
            }
        }

    }

    @Test
    fun testDecodingRouting() {
        val routing = routing()
        val spaceEntry = routing.createRouteFromPath("/a%20b").apply { handle {} }
        val plusEntry = routing.createRouteFromPath("/a+b").apply { handle {} }

        on("resolving /a%20b") {
            val result = resolve(routing, "/a%20b")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to varargEntry") {
                assertEquals(spaceEntry, result.route)
            }
        }

        on("resolving /a+b") {
            val result = resolve(routing, "/a+b")

            it("should successfully resolve") {
                assertTrue(result is RoutingResolveResult.Success)
            }
            it("should resolve to varargEntry") {
                assertEquals(plusEntry, result.route)
            }
        }
    }

    @Test
    fun testTailcardWithPrefix() {
        val routing = routing()
        val prefixChild = routing.route("prefix-{param...}") {
            handle {}
        }

        resolve(routing, "/other").let { result ->
            assertTrue(result is RoutingResolveResult.Failure)
        }
        resolve(routing, "/prefix-").let { result ->
            assertTrue(result is RoutingResolveResult.Success)
            assertSame(prefixChild, result.route)
            assertEquals("", result.parameters["param"])
        }
        resolve(routing, "/prefix-value").let { result ->
            assertTrue(result is RoutingResolveResult.Success)
            assertSame(prefixChild, result.route)
            assertEquals("value", result.parameters["param"])
        }
        resolve(routing, "/prefix-a/b/c").let { result ->
            assertTrue(result is RoutingResolveResult.Success)
            assertSame(prefixChild, result.route)
            assertEquals(listOf("a", "b", "c"), result.parameters.getAll("param"))
        }
    }

    @Test
    fun testRoutingTrailingSlashInLeafRoute() = withTestApplication {
        application.routing {
            get("foo/") {
                call.respondText("foo/")
            }
            get("bar") {
                call.respondText("bar")
            }
        }

        on("making /foo/ request") {
            val result = handleRequest {
                uri = "/foo/"
                method = HttpMethod.Get
            }
            it("/foo/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }
        on("making /foo request") {
            val result = handleRequest {
                uri = "/foo"
                method = HttpMethod.Get
            }
            it("/foo/ should not be called") {
                assertFalse(result.requestHandled)
            }
        }

        on("making /bar request") {
            val result = handleRequest {
                uri = "/bar"
                method = HttpMethod.Get
            }
            it("/bar should not be called") {
                assertEquals("bar", result.response.content)
            }
        }
        on("making /bar/ request") {
            val result = handleRequest {
                uri = "/bar/"
                method = HttpMethod.Get
            }
            it("/bar should not be called") {
                assertFalse(result.requestHandled)
            }
        }
    }

    @Test
    fun testRoutingWithAndWithoutTrailingSlashInLeafRoute() = withTestApplication {
        application.routing {
            get("foo/") {
                call.respondText("foo/")
            }
            get("foo") {
                call.respondText("foo")
            }
        }

        on("making /foo request") {
            val result = handleRequest {
                uri = "/foo"
                method = HttpMethod.Get
            }
            it("/foo should be called") {
                assertEquals("foo", result.response.content)
            }
        }
        on("making /foo/ request") {
            val result = handleRequest {
                uri = "/foo/"
                method = HttpMethod.Get
            }
            it("/foo/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }
    }

    @Test
    fun testRoutingWithTrailingSlashInNonLeafRoute() = withTestApplication {
        application.routing {
            route("foo/") {
                get("bar/") {
                    call.respondText("foo/bar/")
                }
                handle {
                    call.respondText("foo/")
                }
            }
        }

        on("making /foo/bar/ request") {
            val result = handleRequest {
                uri = "/foo/bar/"
                method = HttpMethod.Get
            }
            it("/foo/bar should be called") {
                assertEquals("foo/bar/", result.response.content)
            }
        }

        on("making /foo/ request") {
            val result = handleRequest {
                uri = "/foo/"
                method = HttpMethod.Get
            }
            it("/foo/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }

        on("making /foo request") {
            val result = handleRequest {
                uri = "/foo"
                method = HttpMethod.Get
            }
            it("/foo/ should not be called") {
                assertFalse(result.requestHandled)
            }
        }
    }

    @Test
    fun testRoutingTrailingSlashWithParams() = withTestApplication {
        application.routing {
            get("test/a{foo}b") {
                call.respondText("foo")
            }
            get("test/a{foo}b/") {
                call.respondText("foo/")
            }
        }

        on("making /test/a{foo}b request") {
            val result = handleRequest {
                uri = "/test/afoob"
                method = HttpMethod.Get
            }
            it("/test/a{foo}b should be called") {
                assertEquals("foo", result.response.content)
            }
        }
        on("making /test/a{foo}b/ request") {
            val result = handleRequest {
                uri = "/test/a{foo}b/"
                method = HttpMethod.Get
            }
            it("/test/a{foo}b/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }
    }

    @Test
    fun testRoutingTrailingSlashWithOptionalParams() = withTestApplication {
        application.routing {
            get("test/a{foo?}b") {
                call.respondText("foo")
            }
            get("test/a{foo?}b/") {
                call.respondText("foo/")
            }
        }

        on("making /test/a{foo?}b request") {
            val result = handleRequest {
                uri = "/test/afoob"
                method = HttpMethod.Get
            }
            it("/test/a{foo?}b should be called") {
                assertEquals("foo", result.response.content)
            }
        }
        on("making /test/a{foo?}b/ request") {
            val result = handleRequest {
                uri = "/test/a{foo}b/"
                method = HttpMethod.Get
            }
            it("/test/a{foo?}b/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }
    }

    @Test
    fun testRoutingTrailingSlashWithTrailcard() = withTestApplication {
        application.routing {
            get("test/a{foo...}") {
                call.respondText("foo")
            }
            get("test/a{foo...}/") {
                call.respondText("foo/")
            }
        }

        on("making /test/a{foo...} request") {
            val result = handleRequest {
                uri = "test/aB/C/D"
                method = HttpMethod.Get
            }
            it("/test/a{foo...} should be called") {
                assertEquals("foo", result.response.content)
            }
        }
        on("making /test/a{foo...}/ request") {
            val result = handleRequest {
                uri = "test/aB/C/D/"
                method = HttpMethod.Get
            }
            it("/test/a{foo...}/ should be called") {
                assertEquals("foo/", result.response.content)
            }
        }
    }
}
