package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.routing.*
import org.jetbrains.spek.api.*

class RoutingBuildSpek : Spek() {init {
    given("empty routing") {
        fun On.itShouldHaveSpecificStructure(entry: RoutingEntry) {
            it("should have single child at root") {
                shouldEqual(1, entry.children.size())
            }
            it("should have child of type UriPartConstantRoutingSelector") {
                shouldBeTrue(entry.children[0].selector is UriPartConstantRoutingSelector)
            }
            it("should have child with name 'foo'") {
                shouldEqual("foo", (entry.children[0].selector as UriPartConstantRoutingSelector).name)
            }
            it("should have single child at second level") {
                shouldEqual(1, entry.children[0].entry.children.size())
            }
            it("should have second level child of type UriPartOptionalParameterRoutingSelector") {
                shouldBeTrue(entry.children[0].entry.children[0].selector is UriPartOptionalParameterRoutingSelector)
            }
            it("should have second level child with name 'new'") {
                shouldEqual("new", (entry.children[0].entry.children[0].selector as UriPartOptionalParameterRoutingSelector).name)
            }
        }

        on("adding routing rules manually") {
            val entry = RoutingEntry()
            entry.add(UriPartConstantRoutingSelector("foo"), RoutingEntry())
                    .add(UriPartOptionalParameterRoutingSelector("new"), RoutingEntry())
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string") {
            val entry = RoutingEntry()
            entry.location("/foo/:?new") { }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in nested blocks") {
            val entry = RoutingEntry()
            entry.location("/foo") {
                location("/:?new") { }
            }
            itShouldHaveSpecificStructure(entry)
        }
        on("adding routing from string in separate blocks") {
            val entry = RoutingEntry()
            entry.location("/foo") { }
            entry.location("/foo/:?new") { }
            itShouldHaveSpecificStructure(entry)
        }

        on("creating route with non-optional parameter") {
            val entry = RoutingEntry()
            entry.location("/foo/:new") { }
            it("should have second level child of type UriPartParameterRoutingSelector") {
                shouldBeTrue(entry.children[0].entry.children[0].selector is UriPartParameterRoutingSelector)
            }
            it("should have second level child with name 'new'") {
                shouldEqual("new", (entry.children[0].entry.children[0].selector as UriPartParameterRoutingSelector).name)
            }
        }

        on("creating route with wildcard") {
            val entry = RoutingEntry()
            entry.location("/foo/*") { }
            it("should have second level child of type UriPartWildcardRoutingSelector") {
                shouldBeTrue(entry.children[0].entry.children[0].selector is UriPartWildcardRoutingSelector)
            }
        }
        on("creating route with tailcard") {
            val entry = RoutingEntry()
            entry.location("/foo/**") { }
            it("should have second level child of type UriPartTailcardRoutingSelector") {
                shouldBeTrue(entry.children[0].entry.children[0].selector is UriPartTailcardRoutingSelector)
            }
        }

    }
}
}
