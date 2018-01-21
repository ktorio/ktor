package io.ktor.tests.server.routing

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

private object RootRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        throw UnsupportedOperationException("Root selector should not be evaluated")
    }

    override fun toString(): String = ""
}

fun routing() = Route(parent = null, selector = RootRouteSelector)
fun resolve(routing: Route, path: String, parameters: StringValues = StringValues.Empty, headers: StringValues = StringValues.Empty): RoutingResolveResult {
    return withTestApplication {
        RoutingResolveContext(routing, TestApplicationCall(application).apply {
            request.method = HttpMethod.Get
            request.uri = path + buildString {
                if (!parameters.isEmpty()) {
                    append("?")
                    parameters.formUrlEncodeTo(this)
                }
            }
            headers.flattenEntries().forEach { request.addHeader(it.first, it.second) }
        }).resolve()
    }
}

fun Route.handle(selector: RouteSelector) = createChild(selector).apply { handle {} }

class RoutingResolveTest {
    @Test
    fun `empty routing`() {
        val root = routing()
        val result = resolve(root, "/foo/bar")
        on("resolving any request") {
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.route)
            }
        }
    }

    @Test
    fun `routing with foo`() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo"))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.route)
            }
        }
        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have fooEntry as fail entry") {
                assertEquals(fooEntry, result.route)
            }
        }
    }

    @Test
    fun `routing with foo-bar`() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
        val barEntry = fooEntry.handle(PathSegmentConstantRouteSelector("bar"))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.route)
            }
        }

        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have barEntry as success entry") {
                assertEquals(barEntry, result.route)
            }
        }

        on("resolving /other/bar") {
            val result = resolve(root, "/other/bar")
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.route)
            }
        }
    }

    @Test
    fun `routing foo with parameter`() {
        val root = routing()
        val paramEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentParameterRouteSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"])
            }
        }
    }

    @Test
    fun `routing foo with surrounded parameter`() {
        val root = routing()
        val paramEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentParameterRouteSelector("param", "a", "b"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/avalueb")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"])
            }
        }
    }

    @Test
    fun `routing foo with multiply parameters`() {
        val root = routing()
        root.handle(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentParameterRouteSelector("param1"))
                .handle(PathSegmentParameterRouteSelector("param2"))

        on("resolving /foo/value1/value2") {
            val resolveResult = resolve(root, "/foo/value1/value2")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should have parameter values equal to 'value1' and 'value2'") {
                assertEquals("value1", resolveResult.values["param1"])
                assertEquals("value2", resolveResult.values["param2"])
            }
        }
    }

    @Test
    fun `routing foo with multivalue parameter`() {
        val root = routing()
        root.handle(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentParameterRouteSelector("param"))
                .handle(PathSegmentParameterRouteSelector("param"))

        on("resolving /foo/value1/value2") {
            val resolveResult = resolve(root, "/foo/value1/value2")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should have parameter value equal to [value1, value2]") {
                assertEquals(listOf("value1", "value2"), resolveResult.values.getAll("param"))
            }
        }
    }

    @Test
    fun `routing foo with optional parameter`() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentOptionalParameterRouteSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"])
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should not have parameter value") {
                assertNull(resolveResult.values["param"])
            }
        }
    }

    @Test
    fun `routing foo with wildcard`() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
        val paramEntry = fooEntry.handle(PathSegmentWildcardRouteSelector)

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, resolveResult.route)
            }
        }
    }

    @Test
    fun `routing foo with anonymous tailcard`() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentTailcardRouteSelector())

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = resolve(root, "/foo/bar/baz/blah")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
        }
    }

    @Test
    fun `routing foo with named tailcard`() {
        val root = routing()
        val paramEntry = root.createChild(PathSegmentConstantRouteSelector("foo"))
                .handle(PathSegmentTailcardRouteSelector("items"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value") {
                assertEquals("value", resolveResult.values["items"])
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have empty parameter") {
                assertNull(resolveResult.values["items"])
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = resolve(root, "/foo/bar/baz/blah")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("bar", "baz", "blah"), resolveResult.values.getAll("items"))
            }
        }
    }

    @Test
    fun `routing foo with parameter entry`() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
        val paramEntry = fooEntry.handle(ParameterRouteSelector("name"))

        on("resolving /foo with query string name=value") {
            val resolveResult = resolve(root, "/foo", valuesOf("name" to listOf("value")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("value"), resolveResult.values.getAll("name"))
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, resolveResult.route)
            }
            it("should have no parameter") {
                assertNull(resolveResult.values["name"])
            }
        }

        on("resolving /foo with multiple parameters") {
            val resolveResult = resolve(root, "/foo", valuesOf("name" to listOf("value1", "value2")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value") {
                assertEquals(listOf("value1", "value2"), resolveResult.values.getAll("name"))
            }
        }
    }

    @Test
    fun `routing foo with quality`() {
        val root = routing()
        val fooEntry = root.createChild(PathSegmentConstantRouteSelector("foo"))
        val paramEntry = fooEntry.handle(PathSegmentParameterRouteSelector("name"))
        val constantEntry = fooEntry.handle(PathSegmentConstantRouteSelector("admin"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.route)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["name"])
            }
        }

        on("resolving /foo/admin") {
            val resolveResult = resolve(root, "/foo/admin")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to constantEntry") {
                assertEquals(constantEntry, resolveResult.route)
            }
            it("should not have parameter value") {
                assertNull(resolveResult.values["name"])
            }
        }

    }

    @Test
    fun `routing foo with quality and headers`() {
        val root = routing()
        val fooEntry = root.handle(PathSegmentConstantRouteSelector("foo"))
        val plainEntry = fooEntry.handle(HttpHeaderRouteSelector("Accept", "text/plain"))
        val htmlEntry = fooEntry.handle(HttpHeaderRouteSelector("Accept", "text/html"))

        on("resolving /foo with more specific") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/*, text/html, */*")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, resolveResult.route)
            }
        }

        on("resolving /foo with equal preference") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain, text/html")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, resolveResult.route)
            }
        }

        on("resolving /foo with preference of text/plain") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain, text/html; q=0.5")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, resolveResult.route)
            }
        }

        on("resolving /foo with preference of text/html") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain; q=0.5, text/html")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, resolveResult.route)
            }
        }
    }

    @Test
    fun `select most specific route with root`() {
        val routing = routing()
        val rootEntry = routing.createRouteFromPath("/").apply { handle {} }
        val varargEntry = routing.createRouteFromPath("/{...}").apply { handle {} }

        on("resolving /") {
            val resolveResult = resolve(routing, "/")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to rootEntry") {
                assertEquals(rootEntry, resolveResult.route)
            }
        }
        on("resolving /path") {
            val resolveResult = resolve(routing, "/path")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(varargEntry, resolveResult.route)
            }
        }

    }

    @Test
    fun `select most specific route with optional param`() {
        val routing = routing()
        val dateEntry = routing.createRouteFromPath("/sessions/{date}").apply { handle {} }
        val currentEntry = routing.createRouteFromPath("/sessions/current/{date?}").apply { handle {} }

        on("resolving date") {
            val resolveResult = resolve(routing, "/sessions/2017-11-02")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to rootEntry") {
                assertEquals(dateEntry, resolveResult.route)
            }
        }
        on("resolving current") {
            val resolveResult = resolve(routing, "/sessions/current/2017-11-02T10:00")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(currentEntry, resolveResult.route)
            }
        }
        on("resolving optional current") {
            val resolveResult = resolve(routing, "/sessions/current/")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(currentEntry, resolveResult.route)
            }
        }

    }

    @Test
    fun `decoding routing`() {
        val routing = routing()
        val spaceEntry = routing.createRouteFromPath("/a%20b").apply { handle {} }
        val plusEntry = routing.createRouteFromPath("/a+b").apply { handle {} }

        on("resolving /a%20b") {
            val resolveResult = resolve(routing, "/a%20b")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(spaceEntry, resolveResult.route)
            }
        }

        on("resolving /a+b") {
            val resolveResult = resolve(routing, "/a+b")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(plusEntry, resolveResult.route)
            }
        }
    }

}
