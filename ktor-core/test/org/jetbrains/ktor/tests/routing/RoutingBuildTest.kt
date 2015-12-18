package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class RoutingBuildTest {
    @Test fun `build routing`() {
        fun On.itShouldHaveSpecificStructure(entry: RoutingEntry) {
            it("should have single child at root") {
                assertEquals(1, entry.children.size)
            }
            it("should have correct parent for single child") {
                assertEquals(entry, entry.children[0].parent)
            }
            it("should have child of type UriPartConstantRoutingSelector") {
                assertTrue(entry.children[0].selector is UriPartConstantRoutingSelector)
            }
            it("should have child with name 'foo'") {
                assertEquals("foo", (entry.children[0].selector as UriPartConstantRoutingSelector).name)
            }
            it("should have single child at second level") {
                assertEquals(1, entry.children[0].children.size)
            }
            it("should have second level child of type UriPartOptionalParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartOptionalParameterRoutingSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals("new", (entry.children[0].children[0].selector as UriPartOptionalParameterRoutingSelector).name)
            }
        }

        on("adding routing rules manually") {
            val entry = Routing()
            entry.select(UriPartConstantRoutingSelector("foo"))
                    .select(UriPartOptionalParameterRoutingSelector("new"))
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string") {
            val entry = Routing()
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in nested blocks") {
            val entry = Routing()
            entry.route("/foo") {
                route("/{new?}") { }
            }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in separate blocks") {
            val entry = Routing()
            entry.route("/foo") { }
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }

        on("creating route with non-optional parameter") {
            val entry = Routing()
            entry.route("/foo/{new}") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartParameterRoutingSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals("new", (entry.children[0].children[0].selector as UriPartParameterRoutingSelector).name)
            }
        }

        on("creating route with wildcard") {
            val entry = Routing()
            entry.route("/foo/*") { }
            it("should have second level child of type UriPartWildcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartWildcardRoutingSelector)
            }
        }
        on("creating route with tailcard") {
            val entry = Routing()
            entry.route("/foo/{...}") { }
            it("should have second level child of type UriPartTailcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartTailcardRoutingSelector)
            }
        }

    }
}
