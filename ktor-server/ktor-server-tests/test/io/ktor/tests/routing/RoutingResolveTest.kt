package io.ktor.tests.routing

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.testing.*
import io.ktor.util.*
import org.junit.*
import kotlin.test.*

private object RootRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        throw UnsupportedOperationException("Root selector should not be evaluated")
    }
    override fun toString(): String = ""
}

fun routing() = Route(parent = null, selector = RootRouteSelector)
fun resolve(routing: Route, path: String, parameters: ValuesMap = ValuesMap.Empty): RoutingResolveResult {
    return withTestApplication {
        RoutingResolveContext(routing, TestApplicationCall(application).apply {
            request.method = HttpMethod.Get
            request.uri = path
        }, parameters).resolve()
    }
}

fun resolve(routing: Route, path: String, parameters: ValuesMap = ValuesMap.Empty, headers: ValuesMap = ValuesMap.Empty): RoutingResolveResult {
    return withTestApplication {
        RoutingResolveContext(routing, TestApplicationCall(application).apply {
            request.method = HttpMethod.Get
            request.uri = path
            headers.flattenEntries().forEach { request.addHeader(it.first, it.second) }
        }, parameters, headers).resolve()
    }
}

fun Route.selectHandle(selector: RouteSelector) = select(selector).apply { handle {} }

class RoutingResolveTest {
    @Test fun `empty routing`() {
        val root = routing()
        val result = resolve(root, "/foo/bar")
        on("resolving any request") {
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.entry)
            }
        }
    }

    @Test fun `routing with foo`() {
        val root = routing()
        val fooEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.entry)
            }
        }
        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have fooEntry as fail entry") {
                assertEquals(fooEntry, result.entry)
            }
        }
    }

    @Test fun `routing with foo-bar`() {
        val root = routing()
        val fooEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
        val barEntry = fooEntry.selectHandle(UriPartConstantRouteSelector("bar"))

        on("resolving /foo") {
            val result = resolve(root, "/foo")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.entry)
            }
        }

        on("resolving /foo/bar") {
            val result = resolve(root, "/foo/bar")
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have barEntry as success entry") {
                assertEquals(barEntry, result.entry)
            }
        }

        on("resolving /other/bar") {
            val result = resolve(root, "/other/bar")
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.entry)
            }
        }
    }

    @Test fun `routing foo with parameter`() {
        val root = routing()
        val paramEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartParameterRouteSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"])
            }
        }
    }

    @Test fun `routing foo with surrounded parameter`() {
        val root = routing()
        val paramEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartParameterRouteSelector("param", "a", "b"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/avalueb")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"])
            }
        }
    }

    @Test fun `routing foo with multiply parameters`() {
        val root = routing()
        root.selectHandle(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartParameterRouteSelector("param1"))
                .selectHandle(UriPartParameterRouteSelector("param2"))

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

    @Test fun `routing foo with multivalue parameter`() {
        val root = routing()
        root.selectHandle(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartParameterRouteSelector("param"))
                .selectHandle(UriPartParameterRouteSelector("param"))

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

    @Test fun `routing foo with optional parameter`() {
        val root = routing()
        val paramEntry = root.select(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartOptionalParameterRouteSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
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
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should not have parameter value") {
                assertNull(resolveResult.values["param"])
            }
        }
    }

    @Test fun `routing foo with wildcard`() {
        val root = routing()
        val fooEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
        val paramEntry = fooEntry.selectHandle(UriPartWildcardRouteSelector)

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, resolveResult.entry)
            }
        }
    }

    @Test fun `routing foo with anonymous tailcard`() {
        val root = routing()
        val paramEntry = root.select(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartTailcardRouteSelector())

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = resolve(root, "/foo")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = resolve(root, "/foo/bar/baz/blah")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }
    }

    @Test fun `routing foo with named tailcard`() {
        val root = routing()
        val paramEntry = root.select(UriPartConstantRouteSelector("foo"))
                .selectHandle(UriPartTailcardRouteSelector("items"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
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
                assertEquals(paramEntry, resolveResult.entry)
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
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("bar", "baz", "blah"), resolveResult.values.getAll("items"))
            }
        }
    }

    @Test fun `routing foo with parameter entry`() {
        val root = routing()
        val fooEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
        val paramEntry = fooEntry.selectHandle(ParameterRouteSelector("name"))

        on("resolving /foo with query string name=value") {
            val resolveResult = resolve(root, "/foo", valuesOf("name" to listOf("value")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
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
                assertEquals(fooEntry, resolveResult.entry)
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
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("value1", "value2"), resolveResult.values.getAll("name"))
            }
        }
    }

    @Test fun `routing foo with quality`() {
        val root = routing()
        val fooEntry = root.select(UriPartConstantRouteSelector("foo"))
        val paramEntry = fooEntry.selectHandle(UriPartParameterRouteSelector("name"))
        val constantEntry = fooEntry.selectHandle(UriPartConstantRouteSelector("admin"))

        on("resolving /foo/value") {
            val resolveResult = resolve(root, "/foo/value")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
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
                assertEquals(constantEntry, resolveResult.entry)
            }
            it("should not have parameter value") {
                assertNull(resolveResult.values["name"])
            }
        }

    }

    @Test fun `routing foo with quality and headers`() {
        val root = routing()
        val fooEntry = root.selectHandle(UriPartConstantRouteSelector("foo"))
        val plainEntry = fooEntry.selectHandle(HttpHeaderRouteSelector("Accept", "text/plain"))
        val htmlEntry = fooEntry.selectHandle(HttpHeaderRouteSelector("Accept", "text/html"))

        on("resolving /foo with more specific") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/*, text/html, */*")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, resolveResult.entry)
            }
        }

        on("resolving /foo with equal preference") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain, text/html")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, resolveResult.entry)
            }
        }

        on("resolving /foo with preference of text/plain") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain, text/html; q=0.5")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to plainEntry") {
                assertEquals(plainEntry, resolveResult.entry)
            }
        }

        on("resolving /foo with preference of text/html") {
            val resolveResult = resolve(root, "/foo", headers = valuesOf("Accept" to listOf("text/plain; q=0.5, text/html")))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to htmlEntry") {
                assertEquals(htmlEntry, resolveResult.entry)
            }
        }
    }

    @Test fun `select most specific route with root`() {
        val routing = routing()
        val rootEntry = routing.createRoute("/").apply { handle {} }
        val varargEntry = routing.createRoute("/{...}").apply { handle {} }

        on("resolving /") {
            val resolveResult = resolve(routing, "/")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to rootEntry") {
                assertEquals(rootEntry, resolveResult.entry)
            }
        }
        on("resolving /path") {
            val resolveResult = resolve(routing, "/path")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(varargEntry, resolveResult.entry)
            }
        }

    }

    @Test fun `decoding routing`() {
        val routing = routing()
        val spaceEntry = routing.createRoute("/a%20b").apply { handle {} }
        val plusEntry = routing.createRoute("/a+b").apply { handle {} }

        on("resolving /a%20b") {
            val resolveResult = resolve(routing, "/a%20b")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(spaceEntry, resolveResult.entry)
            }
        }

        on("resolving /a+b") {
            val resolveResult = resolve(routing, "/a+b")

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to varargEntry") {
                assertEquals(plusEntry, resolveResult.entry)
            }
        }
    }

}
