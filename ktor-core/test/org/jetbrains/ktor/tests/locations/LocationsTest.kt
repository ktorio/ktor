package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.reflect.jvm.*
import kotlin.test.*


class LocationsTest {
    @location("/") data class index()

    Test fun `location without URL`() {
        val href = Locations.href(index())
        assertEquals("/", href)

        val testHost = createTestHost()
        testHost.application.locations {
            get<index> {
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/index")
    }

    @location("/about") data class about()

    Test fun `location with URL`() {
        val href = Locations.href(about())
        assertEquals("/about", href)

        val testHost = createTestHost()
        testHost.application.locations {
            get<about> {
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/about/123")
    }

    @location("/user/{id}") data class user(val id: Int)

    Test fun `location with path param`() {
        val href = Locations.href(user(123))
        assertEquals("/user/123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<user> { user ->
                assertEquals(123, user.id)
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/user?id=123")
    }

    @location("/favorite") data class favorite(val id: Int)

    Test fun `location with query param`() {
        val href = Locations.href(favorite(123))
        assertEquals("/favorite?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<favorite> { favorite ->
                assertEquals(123, favorite.id)
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/favorite/123")
        urlShouldBeUnhandled(testHost, "/favorite")
    }

    @location("/container/{id}") data class pathContainer(val id: Int) {
        @location("/items") data class items(val container: pathContainer)
        @location("/items") data class badItems()
    }

    Test fun `location with path parameter and nested data`() {
        val c = pathContainer(123)
        val href = Locations.href(pathContainer.items(c))
        assertEquals("/container/123/items", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                status(HttpStatusCode.OK)
                send()
            }
            failsWith(InconsistentRoutingException::class.java) {
                get<pathContainer.badItems> { }
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/items?id=123")
    }

    @location("/container") data class queryContainer(val id: Int) {
        @location("/items") data class items(val container: queryContainer)
        @location("/items") data class badItems()
    }

    Test fun `location with query parameter and nested data`() {
        val c = queryContainer(123)
        val href = Locations.href(queryContainer.items(c))
        assertEquals("/container/items?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                status(HttpStatusCode.OK)
                send()
            }
            failsWith(InconsistentRoutingException::class.java) {
                get<queryContainer.badItems> { }
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/123/items")
    }

    @location("/container") data class optionalName(val id: Int, val optional: String? = null)

    Test fun `location with missing optional query parameter`() {
        val href = Locations.href(optionalName(123))
        assertEquals("/container?id=123", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalName> {
                assertEquals(123, it.id)
                assertNull(it.optional)
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/container/123")
    }

    Test fun `location with specified optional query parameter`() {
        val href = Locations.href(optionalName(123, "text"))
        assertEquals("/container?id=123&optional=text", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalName> {
                assertEquals(123, it.id)
                assertEquals("text", it.optional)
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/container/123")
    }

    @location("/container/{id?}") data class optionalContainer(val id: Int? = null) {
        @location("/items") data class items(val optional: String? = null)
    }

    Test fun `location with optional path and query parameter`() {
        val href = Locations.href(optionalContainer())
        assertEquals("/container", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<optionalContainer> {
                assertEquals(null, it.id)
                status(HttpStatusCode.OK)
                send()
            }
            get<optionalContainer.items> {
                assertEquals("text", it.optional)
                status(HttpStatusCode.OK)
                send()
            }

        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeHandled(testHost, "/container")
        urlShouldBeHandled(testHost, "/container/123/items?optional=text")
    }

    @location("/container") data class simpleContainer() {
        @location("/items") data class items()
    }

    Test fun `location with simple path container and items`() {
        val href = Locations.href(simpleContainer.items())
        assertEquals("/container/items", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<simpleContainer.items> {
                status(HttpStatusCode.OK)
                send()
            }
            get<simpleContainer> {
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeHandled(testHost, "/container")
        urlShouldBeUnhandled(testHost, "/items")
    }

    @location("/container/{path...}") data class tailCard(val path: List<String>)

    Test fun `location with tailcard`() {
        val href = Locations.href(tailCard(emptyList()))
        assertEquals("/container", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<tailCard> {
                status(HttpStatusCode.OK)
                content(it.path.toString())
                send()
            }

        }
        urlShouldBeHandled(testHost, href, "[]")
        urlShouldBeHandled(testHost, "/container/some", "[some]")
        urlShouldBeHandled(testHost, "/container/123/items?optional=text", "[123, items]")
    }

    @location("/") data class multiquery(val value: List<Int>)
    Test fun `location with multiple query values`() {
        val href = Locations.href(multiquery(listOf(1,2,3)))
        assertEquals("/?value=1&value=2&value=3", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<multiquery> {
                status(HttpStatusCode.OK)
                content(it.value.toString())
                send()
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
            it("should have a response") {
                assertNotNull(result.response)
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK.value, result.response!!.status)
            }
            if (content != null) {
                it("should have a response with content '$content'") {
                    assertEquals(content, result.response!!.content)
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
            it("should have no response") {
                assertNull(result.response)
            }
        }
    }
}
