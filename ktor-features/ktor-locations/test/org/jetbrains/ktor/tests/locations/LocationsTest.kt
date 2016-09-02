package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

private fun withLocationsApplication(test: TestApplicationHost.() -> Unit) = withApplicationFeature<TestApplication> {
    application.install(Locations)
    test()
}

class LocationsTest {
    @location("/") class index()

    @Test fun `location without URL`() = withLocationsApplication {
        val href = application.feature(Locations).href(index())
        assertEquals("/", href)
        application.routing {
            get<index> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/index")
    }

    @location("/about") class about()

    @Test fun `location with URL`() = withLocationsApplication {
        val href = application.feature(Locations).href(about())
        assertEquals("/about", href)
        application.routing {
            get<about> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/about/123")
    }

    @location("/user/{id}") class user(val id: Int)

    @Test fun `location with path param`() = withLocationsApplication {
        val href = application.feature(Locations).href(user(123))
        assertEquals("/user/123", href)
        application.routing {
            get<user> { user ->
                assertEquals(123, user.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/user?id=123")
    }

    @location("/user/{id}/{name}") class named(val id: Int, val name: String)

    @Test fun `location with urlencoded path param`() = withLocationsApplication {
        val href = application.feature(Locations).href(named(123, "abc def"))
        assertEquals("/user/123/abc%20def", href)
        application.routing {
            get<named> { named ->
                assertEquals(123, named.id)
                assertEquals("abc def", named.name)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/user?id=123")
        urlShouldBeUnhandled("/user/123")
    }

    @location("/favorite") class favorite(val id: Int)

    @Test fun `location with query param`() = withLocationsApplication {
        val href = application.feature(Locations).href(favorite(123))
        assertEquals("/favorite?id=123", href)
        application.routing {
            get<favorite> { favorite ->
                assertEquals(123, favorite.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/favorite/123")
        urlShouldBeUnhandled("/favorite")
    }

    @location("/container/{id}") class pathContainer(val id: Int) {
        @location("/items") class items(val container: pathContainer)
        @location("/items") class badItems()
    }

    @Test fun `location with path parameter and nested data`() = withLocationsApplication {
        val c = pathContainer(123)
        val href = application.feature(Locations).href(pathContainer.items(c))
        assertEquals("/container/123/items", href)
        application.routing {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
            assertFailsWith(InconsistentRoutingException::class) {
                get<pathContainer.badItems> {  }
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/items?id=123")
    }

    @location("/container") class queryContainer(val id: Int) {
        @location("/items") class items(val container: queryContainer)
        @location("/items") class badItems()
    }

    @Test fun `location with query parameter and nested data`() = withLocationsApplication {
        val c = queryContainer(123)
        val href = application.feature(Locations).href(queryContainer.items(c))
        assertEquals("/container/items?id=123", href)
        application.routing {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
            assertFailsWith(InconsistentRoutingException::class) {
                get<queryContainer.badItems> {  }
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/123/items")
    }

    @location("/container") class optionalName(val id: Int, val optional: String? = null)

    @Test fun `location with missing optional String parameter`() = withLocationsApplication {
        val href = application.feature(Locations).href(optionalName(123))
        assertEquals("/container?id=123", href)
        application.routing {
            get<optionalName> {
                assertEquals(123, it.id)
                assertNull(it.optional)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }


    @location("/container") class optionalIndex(val id: Int, val optional: Int = 42)

    @Test fun `location with missing optional Int parameter`() = withLocationsApplication {
        val href = application.feature(Locations).href(optionalIndex(123))
        assertEquals("/container?id=123&optional=42", href)
        application.routing {
            get<optionalIndex> {
                assertEquals(123, it.id)
                assertEquals(42, it.optional)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled("/container?id=123")
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }

    @Test fun `location with specified optional query parameter`() = withLocationsApplication {
        val href = application.feature(Locations).href(optionalName(123, "text"))
        assertEquals("/container?id=123&optional=text", href)
        application.routing {
            get<optionalName> {
                assertEquals(123, it.id)
                assertEquals("text", it.optional)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container")
        urlShouldBeUnhandled("/container/123")
    }

    @location("/container/{id?}") class optionalContainer(val id: Int? = null) {
        @location("/items") class items(val optional: String? = null)
    }

    @Test fun `location with optional path and query parameter`() = withLocationsApplication {
        val href = application.feature(Locations).href(optionalContainer())
        assertEquals("/container", href)
        application.routing {
            get<optionalContainer> {
                assertEquals(null, it.id)
                call.respond(HttpStatusCode.OK)
            }
            get<optionalContainer.items> {
                assertEquals("text", it.optional)
                call.respond(HttpStatusCode.OK)
            }

        }
        urlShouldBeHandled(href)
        urlShouldBeHandled("/container")
        urlShouldBeHandled("/container/123/items?optional=text")
    }

    @location("/container") class simpleContainer() {
        @location("/items") class items()
    }

    @Test fun `location with simple path container and items`() = withLocationsApplication {
        val href = application.feature(Locations).href(simpleContainer.items())
        assertEquals("/container/items", href)
        application.routing {
            get<simpleContainer.items> {
                call.respond(HttpStatusCode.OK)
            }
            get<simpleContainer> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeHandled("/container")
        urlShouldBeUnhandled("/items")
    }

    @location("/container/{path...}") class tailCard(val path: List<String>)

    @Test fun `location with tailcard`() = withLocationsApplication {
        val href = application.feature(Locations).href(tailCard(emptyList()))
        assertEquals("/container", href)
        application.routing {
            get<tailCard> {
                call.respond(it.path.toString())
            }

        }
        urlShouldBeHandled(href, "[]")
        urlShouldBeHandled("/container/some", "[some]")
        urlShouldBeHandled("/container/123/items?optional=text", "[123, items]")
    }

    @location("/") class multiquery(val value: List<Int>)

    @Test fun `location with multiple query values`() = withLocationsApplication {
        val href = application.feature(Locations).href(multiquery(listOf(1, 2, 3)))
        assertEquals("/?value=1&value=2&value=3", href)
        application.routing {
            get<multiquery> {
                call.respond(it.value.toString())
            }

        }
        urlShouldBeHandled(href, "[1, 2, 3]")
    }

    @location("/space in") class SpaceInPath()
    @location("/plus+in") class PlusInPath()

    @Test
    fun testURLBuilder() = withLocationsApplication {
        application.routing {
            handle {
                assertEquals("http://localhost/container?id=1&optional=ok", call.url(optionalName(1, "ok")))
                assertEquals("http://localhost/container?id=1&optional=ok%2B.plus", call.url(optionalName(1, "ok+.plus")))
                assertEquals("http://localhost/container?id=1&optional=ok+space", call.url(optionalName(1, "ok space")))

                assertEquals("http://localhost/space%20in", call.url(SpaceInPath()))
                assertEquals("http://localhost/plus+in", call.url(PlusInPath()))

                call.respondText(call.url(optionalName(1, "ok")))
            }
        }

        urlShouldBeHandled("/", "http://localhost/container?id=1&optional=ok")
    }
}

