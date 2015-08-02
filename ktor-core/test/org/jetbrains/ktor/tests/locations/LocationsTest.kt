package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.tests.*
import org.junit.*
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

    @location("/user/:id") data class user(val id: Int)

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
    }

    @location("/container/:id") data class container(val id: Int) {
        @location("/items") data class items(val container: container)
    }

    Test fun `location with query parameter and nested data`() {
        val c = container(123)
        val href = Locations.href(container.items(c))
        assertEquals("/container/123/items", href)
        val testHost = createTestHost()
        testHost.application.locations {
            get<container.items> { items ->
                assertEquals(123, items.container.id)
                status(HttpStatusCode.OK)
                send()
            }
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/items?id=123")
    }

    @location("/container") data class queryContainer(val id: Int) {
        @location("/items") data class items(val container: queryContainer)
    }

    Test fun `location with path parameter and nested data`() {
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
        }
        urlShouldBeHandled(testHost, href)
        urlShouldBeUnhandled(testHost, "/container/items")
        urlShouldBeUnhandled(testHost, "/container/123/items")
    }

    private fun urlShouldBeHandled(testHost: TestApplicationHost, url: String) {
        on("making get request to $url") {
            val result = testHost.handleRequest {
                uri = url
                httpMethod = HttpMethod.Get
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
        }
    }

    private fun urlShouldBeUnhandled(testHost: TestApplicationHost, url: String) {
        on("making post request to $url") {
            val result = testHost.handleRequest {
                uri = url
                httpMethod = HttpMethod.Post
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
