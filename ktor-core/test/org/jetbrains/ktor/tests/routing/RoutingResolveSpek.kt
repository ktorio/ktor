package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.spek.api.*
import org.junit.*

class RoutingResolveSpek {
    Test fun `empty routing`() {
        val entry = RoutingEntry()
        val result = entry.resolve(RoutingResolveContext("/foo/bar"))
        on("resolving any request") {
            it("should not succeed") {
                shouldBeFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                shouldEqual(entry, result.entry)
            }
        }
    }

    Test fun `routing with foo`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry)

        on("resolving /foo") {
            val result = entry.resolve(RoutingResolveContext("/foo"))
            it("should succeed") {
                shouldBeTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                shouldEqual(fooEntry, result.entry)
            }
        }
        on("resolving /foo/bar") {
            val result = entry.resolve(RoutingResolveContext("/foo/bar"))
            it("should not succeed") {
                shouldBeFalse(result.succeeded)
            }
            it("should have fooEntry as fail entry") {
                shouldEqual(fooEntry, result.entry)
            }
        }
    }

    Test fun `routing with foo-bar`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val barEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry)
        fooEntry.add(UriPartConstantRoutingSelector("bar"), barEntry)

        on("resolving /foo") {
            val result = entry.resolve(RoutingResolveContext("/foo"))
            it("should succeed") {
                shouldBeTrue(result.succeeded)
            }
            it("should have fooEntry as success entry") {
                shouldEqual(fooEntry, result.entry)
            }
        }

        on("resolving /foo/bar") {
            val result = entry.resolve(RoutingResolveContext("/foo/bar"))
            it("should succeed") {
                shouldBeTrue(result.succeeded)
            }
            it("should have barEntry as success entry") {
                shouldEqual(barEntry, result.entry)
            }
        }

        on("resolving /other/bar") {
            val result = entry.resolve(RoutingResolveContext("/other/bar"))
            it("should not succeed") {
                shouldBeFalse(result.succeeded)
            }
            it("should have root as fail entry") {
                shouldEqual(entry, result.entry)
            }
        }
    }

    Test fun `routing foo with parameter`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(UriPartParameterRoutingSelector("param"), paramEntry)

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                shouldEqual("value", resolveResult.values["param"]?.first())
            }
        }
    }

    Test fun `routing foo with multiply parameters`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry)
                .add(UriPartParameterRoutingSelector("param1"), RoutingEntry())
                .add(UriPartParameterRoutingSelector("param2"), RoutingEntry())

        on("resolving /foo/value1/value2") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value1/value2"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should have parameter values equal to 'value1' and 'value2'") {
                shouldEqual("value1", resolveResult.values["param1"]?.first())
                shouldEqual("value2", resolveResult.values["param2"]?.first())
            }
        }
    }

    Test fun `routing foo with multivalue parameter`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry)
                .add(UriPartParameterRoutingSelector("param"), RoutingEntry())
                .add(UriPartParameterRoutingSelector("param"), RoutingEntry())

        on("resolving /foo/value1/value2") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value1/value2"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should have parameter value equal to [value1, value2]") {
                shouldEqual(listOf("value1", "value2"), resolveResult.values["param"])
            }
        }
    }

    Test fun `routing foo with optional parameter`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(UriPartOptionalParameterRoutingSelector("param"), paramEntry)

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value equal to 'value'") {
                shouldEqual("value", resolveResult.values["param"]?.first())
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should not have parameter value") {
                shouldBeNull(resolveResult.values["param"])
            }
        }
    }

    Test fun `routing foo with wildcard`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(UriPartWildcardRoutingSelector(), paramEntry)

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                shouldEqual(fooEntry, resolveResult.entry)
            }
        }
    }

    Test fun `routing foo with anonymous tailcard`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(UriPartTailcardRoutingSelector(), paramEntry)

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/bar/baz/blah"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
        }
    }

    Test fun `routing foo with named tailcard`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(UriPartTailcardRoutingSelector("items"), paramEntry)

        on("resolving /foo/value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/value"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                shouldEqual(listOf("value"), resolveResult.values["items"])
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have empty parameter") {
                shouldBeTrue(resolveResult.values["items"]?.none())
            }
        }

        on("resolving /foo/bar/baz/blah") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo/bar/baz/blah"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                shouldEqual(listOf("bar", "baz", "blah"), resolveResult.values["items"])
            }
        }
    }

    Test fun `routing foo with parameter entry`() {
        val entry = RoutingEntry()
        val fooEntry = RoutingEntry()
        val paramEntry = RoutingEntry()
        entry.add(UriPartConstantRoutingSelector("foo"), fooEntry).add(ParameterRoutingSelector("name"), paramEntry)

        on("resolving /foo with query string name=value") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo", mapOf("name" to listOf("value"))))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                shouldEqual(listOf("value"), resolveResult.values["name"])
            }
        }

        on("resolving /foo") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo"))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to fooEntry") {
                shouldEqual(fooEntry, resolveResult.entry)
            }
            it("should have no parameter") {
                shouldBeNull(resolveResult.values["name"])
            }
        }

        on("resolving /foo with multiple parameters") {
            val resolveResult = entry.resolve(RoutingResolveContext("/foo", mapOf("name" to listOf("value1", "value2"))))

            it("should successfully resolve") {
                shouldBeTrue(resolveResult.succeeded)
            }
            it("should resolve to paramEntry") {
                shouldEqual(paramEntry, resolveResult.entry)
            }
            it("should have parameter value") {
                shouldEqual(listOf("value1", "value2"), resolveResult.values["name"])
            }
        }
    }
}
