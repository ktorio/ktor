package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class LocationsTest {
    @location("/") class index()

    @Test fun `location without URL`() {
        val href = Locations.href(index())
        assertEquals("/", href)

        val testHost = createTestHost()
        testHost.application.locations {
            get<index> {
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/index")
    }

    @location("/about") class about()

    @Test fun `location with URL`() {
        val href = Locations.href(about())
        assertEquals("/about", href)

        val testHost = createTestHost()
        testHost.application.locations {
            get<about> {
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/about/123")
    }

    @location("/user/{id}") class user(val id: Int)

    @Test fun `location with path param`() {
        val href = Locations.href(user(123))
        assertEquals("/user/123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<user> { user ->
                assertEquals(123, user.id)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/user?id=123")
    }

    @location("/favorite") class favorite(val id: Int)

    @Test fun `location with query param`() {
        val href = Locations.href(favorite(123))
        assertEquals("/favorite?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<favorite> { favorite ->
                assertEquals(123, favorite.id)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/favorite/123")
        urlShouldBeUnhandled(testHost, "/favorite")
    }

    @location("/container/{id}") class pathContainer(val id: Int) {
        @location("/items") class items(val container: pathContainer)
        @location("/items") class badItems()
    }

    @Test fun `location with path parameter and nested data`() {
        val c = pathContainer(123)
        val href = Locations.href(pathContainer.items(c))
        assertEquals("/container/123/items", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
            assertFailsWith(InconsistentRoutingException::class.java) {
                get<pathContainer.badItems> { ApplicationRequestStatus.Handled }
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/items?id=123")
    }

    @location("/container") class queryContainer(val id: Int) {
        @location("/items") class items(val container: queryContainer)
        @location("/items") class badItems()
    }

    @Test fun `location with query parameter and nested data`() {
        val c = queryContainer(123)
        val href = Locations.href(queryContainer.items(c))
        assertEquals("/container/items?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
            assertFailsWith(InconsistentRoutingException::class.java) {
                get<queryContainer.badItems> { ApplicationRequestStatus.Handled }
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/123/items")
    }

    @location("/container") class optionalName(val id: Int, val optional: String? = null)

    @Test fun `location with missing optional String parameter`() {
        val href = Locations.href(optionalName(123))
        assertEquals("/container?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalName> {
                assertEquals(123, it.id)
                assertNull(it.optional)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/container/123")
    }


    @location("/container") class optionalIndex(val id: Int, val optional: Int = 42)

    @Test fun `location with missing optional Int parameter`() {
        val href = Locations.href(optionalIndex(123))
        assertEquals("/container?id=123&optional=42", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalIndex> {
                assertEquals(123, it.id)
                assertEquals(42, it.optional)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, "/container?id=123")
        urlShouldBeUnhandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/container/123")
    }

    @Test fun `location with specified optional query parameter`() {
        val href = Locations.href(optionalName(123, "text"))
        assertEquals("/container?id=123&optional=text", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalName> {
                assertEquals(123, it.id)
                assertEquals("text", it.optional)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/container/123")
    }

    @location("/container/{id?}") class optionalContainer(val id: Int? = null) {
        @location("/items") class items(val optional: String? = null)
    }

    @Test fun `location with optional path and query parameter`() {
        val href = Locations.href(optionalContainer())
        assertEquals("/container", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalContainer> {
                assertEquals(null, it.id)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
            get<optionalContainer.items> {
                assertEquals("text", it.optional)
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }

        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeHandled(testHost, "/container")
        urlShouldBeHandled(testHost, "/container/123/items?optional=text")
    }

    @location("/container") class simpleContainer() {
        @location("/items") class items()
    }

    @Test fun `location with simple path container and items`() {
        val href = Locations.href(simpleContainer.items())
        assertEquals("/container/items", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<simpleContainer.items> {
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
            get<simpleContainer> {
                response.status(HttpStatusCode.OK)
                ApplicationRequestStatus.Handled
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeHandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/items")
    }

    @location("/container/{path...}") class tailCard(val path: List<String>)

    @Test fun `location with tailcard`() {
        val href = Locations.href(tailCard(emptyList()))
        assertEquals("/container", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<tailCard> {
                response.sendText(it.path.toString())
            }

        }
        urlShouldBeHandled(testHost, href, "[]")
        urlShouldBeHandled(testHost, "/container/some", "[some]")
        urlShouldBeHandled(testHost, "/container/123/items?optional=text", "[123, items]")
    }

    @location("/") class multiquery(val value: List<Int>)

    @Test fun `location with multiple query values`() {
        val href = Locations.href(multiquery(listOf(1, 2, 3)))
        assertEquals("/?value=1&value=2&value=3", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<multiquery> {
                response.sendText(it.value.toString())
            }

        }
        urlShouldBeHandled(testHost, href, "[1, 2, 3]")
    }

    private fun urlShouldBeHandled(testHost: TestApplicationHost, url: String, content: String? = null) {
        on("making get request to $url") {
            val result = testHost.handleRequest {
                uri = url
                method = HttpMethod.Get
            }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
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

    private fun urlShouldBeUnhandled(testHost: TestApplicationHost, url: String) {
        on("making post request to $url") {
            val result = testHost.handleRequest {
                uri = url
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, result.requestResult)
            }
        }
    }
}
