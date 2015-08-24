package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class RoutingResolveTest {
    Test fun `empty routing`() {
        val root = Routing()
        val result = root.resolve(RoutingResolveContext("/foo/bar"))
        on("resolving any request") {
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.entry)
            }
        }
    }

    Test fun `routing with foo`() {
        val root = Routing()
        val fooEntry = root.select(UriPartConstantRoutingSelector("foo"))

        on("resolving /foo") {
            val result = root.resolve(RoutingResolveContext("/foo"))
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.entry)
            }
        }
        on("resolving /foo/bar") {
            val result = root.resolve(RoutingResolveContext("/foo/bar"))
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have fooEntry as fail entry") {
                assertEquals(fooEntry, result.entry)
            }
        }
    }

    Test fun `routing with foo-bar`() {
        val root = Routing()
        val fooEntry = root.select(UriPartConstantRoutingSelector("foo"))
        val barEntry = fooEntry.select(UriPartConstantRoutingSelector("bar"))

        on("resolving /foo") {
            val result = root.resolve(RoutingResolveContext("/foo"))
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                assertEquals(fooEntry, result.entry)
            }
        }

        on("resolving /foo/bar") {
            val result = root.resolve(RoutingResolveContext("/foo/bar"))
            it("should succeed") {
                assertTrue(result.succeeded)
            }
            it("should have barEntry as success entry") {
                assertEquals(barEntry, result.entry)
            }
        }

        on("resolving /other/bar") {
            val result = root.resolve(RoutingResolveContext("/other/bar"))
            it("should not succeed") {
                assertFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                assertEquals(root, result.entry)
            }
        }
    }

    Test fun `routing foo with parameter`() {
        val root = Routing()
        val paramEntry = root.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartParameterRoutingSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"]?.first())
            }
        }
    }

    Test fun `routing foo with multiply parameters`() {
        val root = Routing()
        root.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartParameterRoutingSelector("param1"))
                .select(UriPartParameterRoutingSelector("param2"))

        on("resolving /foo/value1/value2") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value1/value2"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should have parameter values equal to 'value1' and 'value2'") {
                assertEquals("value1", resolveResult.values["param1"]?.first())
                assertEquals("value2", resolveResult.values["param2"]?.first())
            }
        }
    }

    Test fun `routing foo with multivalue parameter`() {
        val root = Routing()
        root.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartParameterRoutingSelector("param"))
                .select(UriPartParameterRoutingSelector("param"))

        on("resolving /foo/value1/value2") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value1/value2"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should have parameter value equal to [value1, value2]") {
                assertEquals(listOf("value1", "value2"), resolveResult.values["param"])
            }
        }
    }

    Test fun `routing foo with optional parameter`() {
        val root = Routing()
        val paramEntry = root.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartOptionalParameterRoutingSelector("param"))

        on("resolving /foo/value") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                assertEquals("value", resolveResult.values["param"]?.first())
            }
        }

        on("resolving /foo") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo"))

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

    Test fun `routing foo with wildcard`() {
        val root = Routing()
        val fooEntry = root.select(UriPartConstantRoutingSelector("foo"))
        val paramEntry = fooEntry
                .select(UriPartWildcardRoutingSelector())

        on("resolving /foo/value") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(fooEntry, resolveResult.entry)
            }
        }
    }

    Test fun `routing foo with anonymous tailcard`() {
        val root = Routing()
        val paramEntry = root.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartTailcardRoutingSelector())

        on("resolving /foo/value") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo/bar/baz/blah"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
        }
    }

    Test fun `routing foo with named tailcard`() {
        val entry = Routing()
        val paramEntry = entry.select(UriPartConstantRoutingSelector("foo"))
                .select(UriPartTailcardRoutingSelector("items"))

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("value"), resolveResult.values["items"])
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have empty parameter") {
                assertTrue(resolveResult.values["items"]?.none() ?: true)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/bar/baz/blah"))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("bar", "baz", "blah"), resolveResult.values["items"])
            }
        }
    }

    Test fun `routing foo with parameter entry`() {
        val root = Routing()
        val fooEntry = root.select(UriPartConstantRoutingSelector("foo"))
        val paramEntry = fooEntry.select(ParameterRoutingSelector("name"))

        on("resolving /foo with query string name=value") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo", valuesOf("name" to listOf("value"))))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("value"), resolveResult.values["name"])
            }
        }

        on("resolving /foo") {
            val resolveResult = root.resolve(RoutingResolveContext("/foo"))

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
            val resolveResult = root.resolve(RoutingResolveContext("/foo", valuesOf("name" to listOf("value1", "value2"))))

            it("should successfully resolve") {
                assertTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                assertEquals(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                assertEquals(listOf("value1", "value2"), resolveResult.values["name"])
            }
        }
    }
}
