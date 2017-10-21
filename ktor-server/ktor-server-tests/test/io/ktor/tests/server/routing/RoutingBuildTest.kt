package io.ktor.tests.server.routing

import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.*
import kotlin.test.*

class RoutingBuildTest {
    @Test fun `build routing`() {
        fun On.itShouldHaveSpecificStructure(entry: Route) {
            it("should have single child at root") {
                assertEquals(1, entry.children.size)
            }
            it("should have correct parent for single child") {
                assertEquals(entry, entry.children[0].parent)
            }
            it("should have child of type UriPartConstantRoutingSelector") {
                assertTrue(entry.children[0].selector is UriPartConstantRouteSelector)
            }
            it("should have child with name 'foo'") {
                assertEquals("foo", (entry.children[0].selector as UriPartConstantRouteSelector).name)
            }
            it("should have single child at second level") {
                assertEquals(1, entry.children[0].children.size)
            }
            it("should have second level child of type UriPartOptionalParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartOptionalParameterRouteSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals("new", (entry.children[0].children[0].selector as UriPartOptionalParameterRouteSelector).name)
            }
        }

        on("adding routing rules manually") {
            val entry = routing()
            entry.select(UriPartConstantRouteSelector("foo"))
                    .select(UriPartOptionalParameterRouteSelector("new"))
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string") {
            val entry = routing()
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in nested blocks") {
            val entry = routing()
            entry.route("/foo") {
                route("/{new?}") { }
            }
            itShouldHaveSpecificStructure(entry)
        }

        on("adding routing from string in separate blocks") {
            val entry = routing()
            entry.route("/foo") { }
            entry.route("/foo/{new?}") { }
            itShouldHaveSpecificStructure(entry)
        }

        on("creating route with non-optional parameter") {
            val entry = routing()
            entry.route("/foo/{new}") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartParameterRouteSelector)
            }
            it("should have second level child with name 'new'") {
                assertEquals("new", (entry.children[0].children[0].selector as UriPartParameterRouteSelector).name)
            }
        }

        on("creating route with surrounded parameter") {
            val entry = routing()
            entry.route("/foo/a{new}b") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                val selector = entry.children[0].children[0].selector as? UriPartParameterRouteSelector
                assertNotNull(selector)
                assertEquals("new", selector?.name)
                assertEquals("a", selector?.prefix)
                assertEquals("b", selector?.suffix)
            }
        }

        on("creating route with wildcard") {
            val entry = routing()
            entry.route("/foo/*") { }
            it("should have second level child of type UriPartWildcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartWildcardRouteSelector)
            }
        }
        on("creating route with tailcard") {
            val entry = routing()
            entry.route("/foo/{...}") { }
            it("should have second level child of type UriPartTailcardRoutingSelector") {
                assertTrue(entry.children[0].children[0].selector is UriPartTailcardRouteSelector)
            }
        }

    }
}
