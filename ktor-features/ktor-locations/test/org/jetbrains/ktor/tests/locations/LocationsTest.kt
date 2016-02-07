package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

fun withLocationsApplication(test: TestApplicationHost.() -> Unit) = withApplication<TestApplication> {
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/user?id=123")
    }

    @location("/user/{id}/{name}") class named(val id: Int, val name: String)

    @Test fun `location with urlencoded path param`() = withLocationsApplication {
        val href = application.feature(Locations).href(named(123, "abc def"))
        assertEquals("/user/123/abc+def", href)
        application.routing {
            get<named> { named ->
                assertEquals(123, named.id)
                assertEquals("abc def", named.name)
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
            }
            assertFailsWith(InconsistentRoutingException::class) {
                get<pathContainer.badItems> { ApplicationCallResult.Handled }
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
            }
            assertFailsWith(InconsistentRoutingException::class) {
                get<queryContainer.badItems> { ApplicationCallResult.Handled }
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
            }
            get<optionalContainer.items> {
                assertEquals("text", it.optional)
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
            }
            get<simpleContainer> {
                response.status(HttpStatusCode.OK)
                ApplicationCallResult.Handled
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
                response.sendText(it.path.toString())
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
                response.sendText(it.value.toString())
            }

        }
        urlShouldBeHandled(href, "[1, 2, 3]")
    }

    @location("/space in") class SpaceInPath()
    @location("/plus+in") class PlusInPath()

    @Test
    fun testURLBuilder() {
        withLocationsApplication {
            assertEquals("http://localhost/container?id=1&optional=ok", application.url(optionalName(1, "ok")))
            assertEquals("http://localhost/container?id=1&optional=ok%2B.plus", application.url(optionalName(1, "ok+.plus")))
            assertEquals("http://localhost/container?id=1&optional=ok+space", application.url(optionalName(1, "ok space")))

            assertEquals("http://localhost/space%20in", application.url(SpaceInPath()))
            assertEquals("http://localhost/plus+in", application.url(PlusInPath()))

            application.routing {
                handle {
                    response.sendText(url(optionalName(1, "ok")))
                }
            }

            urlShouldBeHandled("/", "http://localhost/container?id=1&optional=ok")
        }
    }

    private fun TestApplicationHost.urlShouldBeHandled(url: String, content: String? = null) {
        on("making get request to $url") {
            val result = handleRequest {
                uri = url
                method = HttpMethod.Get
            }
            it("should be handled") {
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            if (content != null) {
                it("should have a response with content '$content'") {
                    assertEquals(content, result.response.content)
                }
            }
        }
    }

    private fun TestApplicationHost.urlShouldBeUnhandled(url: String) {
        on("making post request to $url") {
            val result = handleRequest {
                uri = url
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
        }
    }
}
