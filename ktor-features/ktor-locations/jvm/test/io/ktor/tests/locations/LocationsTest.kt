/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import java.math.*
import kotlin.test.*

@OptIn(KtorExperimentalLocationsAPI::class)
private fun withLocationsApplication(test: TestApplicationEngine.() -> Unit) = withTestApplication {
    application.install(Locations)
    test()
}

@OptIn(KtorExperimentalLocationsAPI::class)
class LocationsTest {
    @Location("/")
    class index

    @Test fun `location without URL`() = withLocationsApplication {
        val href = application.locations.href(index())
        assertEquals("/", href)
        application.routing {
            get<index> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/index")
    }

    @Test fun `locationLocal`() {
        // ^^^ do not add spaces to method name, inline breaks
        @Location("/")
        class indexLocal
        withLocationsApplication {
            val href = application.locations.href(indexLocal())
            assertEquals("/", href)
            application.routing {
                get<indexLocal> {
                    call.respond(HttpStatusCode.OK)
                }
            }
            urlShouldBeHandled(href)
            urlShouldBeUnhandled("/index")
        }
    }

    @Location("/about")
    class about

    @Test fun `location with URL`() = withLocationsApplication {
        val href = application.locations.href(about())
        assertEquals("/about", href)
        application.routing {
            get<about> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/about/123")
    }

    @Location("/error")
    class Build(val build: String) {
        init {
            require(build.length > 0)
        }
    }

    @Test fun testLocationWithException() = withLocationsApplication {
        application.routing {
            get<Build> {
            }
        }

        assertFailsWith<IllegalArgumentException> {
            urlShouldBeHandled("/error?build=")
        }
    }

    @Location("/user/{id}")
    class user(val id: Int)

    @Test fun `location with path param`() = withLocationsApplication {
        val href = application.locations.href(user(123))
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

    @Location("/user/{id}/{name}")
    class named(val id: Int, val name: String)

    @Test fun `location with urlencoded path param`() = withLocationsApplication {
        val href = application.locations.href(named(123, "abc def"))
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

    @Location("/favorite")
    class favorite(val id: Int)

    @Test fun `location with query param`() = withLocationsApplication {
        val href = application.locations.href(favorite(123))
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

    @Location("/container/{id}")
    class pathContainer(val id: Int) {
        @Location("/items")
        class items(val container: pathContainer)
        @Location("/items")
        class badItems
    }

    @Test fun `location with path parameter and nested data`() = withLocationsApplication {
        val c = pathContainer(123)
        val href = application.locations.href(pathContainer.items(c))
        assertEquals("/container/123/items", href)
        application.routing {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
            assertFailsWith(LocationRoutingException::class) {
                get<pathContainer.badItems> { }
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/items?id=123")
    }

    @Location("/container")
    class queryContainer(val id: Int) {
        @Location("/items")
        class items(val container: queryContainer)
        @Location("/items")
        class badItems
    }

    @Test fun `location with query parameter and nested data`() = withLocationsApplication {
        val c = queryContainer(123)
        val href = application.locations.href(queryContainer.items(c))
        assertEquals("/container/items?id=123", href)
        application.routing {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
            assertFailsWith(LocationRoutingException::class) {
                get<queryContainer.badItems> { }
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/123/items")
    }

    @Location("/container")
    class optionalName(val id: Int, val optional: String? = null)

    @Test fun `location with missing optional String parameter`() = withLocationsApplication {
        val href = application.locations.href(optionalName(123))
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

    @Location("/container")
    class optionalIndex(val id: Int, val optional: Int = 42)

    @Test fun `location with missing optional Int parameter`() = withLocationsApplication {
        val href = application.locations.href(optionalIndex(123))
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
        val href = application.locations.href(optionalName(123, "text"))
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

    @Location("/container/{id?}")
    class optionalContainer(val id: Int? = null) {
        @Location("/items")
        class items(val optional: String? = null)
    }

    @Test fun `location with optional path and query parameter`() = withLocationsApplication {
        val href = application.locations.href(optionalContainer())
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

    @Location("/container")
    class simpleContainer {
        @Location("/items")
        class items
    }

    @Test fun `location with simple path container and items`() = withLocationsApplication {
        val href = application.locations.href(simpleContainer.items())
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

    @Location("/container/{path...}")
    class tailCard(val path: List<String>)

    @Test fun `location with tailcard`() = withLocationsApplication {
        val href = application.locations.href(tailCard(emptyList()))
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

    @Location("/")
    class multiquery(val value: List<Int>)
    @Location("/")
    class multiquery2(val name: List<String>)

    @Test fun `location with multiple query values`() = withLocationsApplication {
        val href = application.locations.href(multiquery(listOf(1, 2, 3)))
        assertEquals("/?value=1&value=2&value=3", href)
        application.routing {
            get<multiquery> {
                call.respond(it.value.toString())
            }
        }
        urlShouldBeHandled(href, "[1, 2, 3]")
    }

    @Test fun `location with multiple query values can select by query params`() = withLocationsApplication {
        val href = application.locations.href(multiquery(listOf(1)))
        assertEquals("/?value=1", href)
        application.routing {
            get<multiquery> {
                call.respond("1: ${it.value}")
            }
            get<multiquery2> {
                call.respond("2: ${it.name}")
            }
        }
        urlShouldBeHandled(href, "1: [1]")
    }

    @Test fun `location with multiple query values can select by query params2`() = withLocationsApplication {
        val href = application.locations.href(multiquery2(listOf("john, mary")))
        assertEquals("/?name=john%2C+mary", href)
        application.routing {
            get<multiquery> {
                call.respond("1: ${it.value}")
            }
            get<multiquery2> {
                call.respond("2: ${it.name}")
            }
        }
        urlShouldBeHandled(href, "2: [john, mary]")
    }

    @Location("/")
    class multiqueryWithDefault(val value: List<Int> = emptyList())

    @Test fun `location with multiple query values and default`() = withLocationsApplication {
        val href = application.locations.href(multiqueryWithDefault(listOf()))
        assertEquals("/", href)
        application.routing {
            get<multiqueryWithDefault> {
                call.respond(it.value.toString())
            }
        }
        urlShouldBeHandled(href, "[]")
    }

    @Location("/space in")
    class SpaceInPath
    @Location("/plus+in")
    class PlusInPath

    @Test
    fun testURLBuilder() = withLocationsApplication {
        application.routing {
            handle {
                assertEquals(
                    "http://localhost/container?id=1&optional=ok",
                    call.url(optionalName(1, "ok"))
                )
                assertEquals(
                    "http://localhost/container?id=1&optional=ok%2B.plus",
                    call.url(optionalName(1, "ok+.plus"))
                )
                assertEquals(
                    "http://localhost/container?id=1&optional=ok+space",
                    call.url(optionalName(1, "ok space"))
                )

                assertEquals("http://localhost/space%20in", call.url(SpaceInPath()))
                assertEquals("http://localhost/plus+in", call.url(PlusInPath()))

                call.respondText(call.url(optionalName(1, "ok")))
            }
        }

        urlShouldBeHandled("/", "http://localhost/container?id=1&optional=ok")
    }

    @Location("/")
    object root

    @Test fun `location root by object`() = withLocationsApplication {
        val href = application.locations.href(root)
        assertEquals("/", href)
        application.routing {
            get<root> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/index")
    }

    @Location("/help")
    object help

    @Test fun `location by object`() = withLocationsApplication {
        val href = application.locations.href(help)
        assertEquals("/help", href)
        application.routing {
            get<help> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/help/123")
    }

    @Location("/users")
    object users {
        @Location("/me")
        object me
        @Location("/{id}")
        class user(val id: Int)
    }

    @Test fun `location by object in object`() = withLocationsApplication {
        val href = application.locations.href(users.me)
        assertEquals("/users/me", href)
        application.routing {
            get<users.me> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/users/123")
    }

    @Test fun `location by class in object`() = withLocationsApplication {
        val href = application.locations.href(users.user(123))
        assertEquals("/users/123", href)
        application.routing {
            get<users.user> { user ->
                assertEquals(123, user.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/users/me")
    }

    @Location("/items/{id}")
    object items

    @Test(expected = IllegalArgumentException::class)
    fun `location by object has bind argument`() =
        withLocationsApplication {
            application.locations.href(items)
        }

    @Location("/items/{itemId}/{extra?}")
    class OverlappingPath1(val itemId: Int, val extra: String?)

    @Location("/items/{extra}")
    class OverlappingPath2(val extra: String)

    @Test fun `overlapping paths are resolved as expected`() = withLocationsApplication {
        application.install(CallLogging)
        application.routing {
            get<OverlappingPath1> {
                call.respond(HttpStatusCode.OK)
            }
            get<OverlappingPath2> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(application.locations.href(OverlappingPath1(1, "Foo")))
        urlShouldBeUnhandled(application.locations.href(OverlappingPath2("1-Foo")))
    }

    enum class LocationEnum {
        A, B, C
    }

    @Location("/")
    class LocationWithEnum(val e: LocationEnum)

    @Test fun `location class with enum value`() = withLocationsApplication {
        application.routing {
            get<LocationWithEnum> {
                call.respondText(call.locations.resolve<LocationWithEnum>(call).e.name)
            }
        }

        urlShouldBeHandled("/?e=A", "A")
        urlShouldBeHandled("/?e=B", "B")

        handleRequest(HttpMethod.Get, "/?e=x").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Location("/")
    class LocationWithBigNumbers(val bd: BigDecimal, val bi: BigInteger)

    @Test fun `location class with big numbers`() = withLocationsApplication {
        val bd = BigDecimal("123456789012345678901234567890")
        val bi = BigInteger("123456789012345678901234567890")

        application.routing {
            get<LocationWithBigNumbers> { location ->
                assertEquals(bd, location.bd)
                assertEquals(bi, location.bi)

                call.respondText(call.locations.href(location))
            }
        }

        urlShouldBeHandled(
            "/?bd=123456789012345678901234567890&bi=123456789012345678901234567890",
            "/?bd=123456789012345678901234567890&bi=123456789012345678901234567890"
        )
    }

    @Test fun `location parameter mismatch should lead to bad request status`() = withLocationsApplication {
        @Location("/")
        data class L(val text: String, val number: Int, val longNumber: Long)

        application.routing {
            get<L> { instance ->
                call.respondText(
                    "text = ${instance.text}, number = ${instance.number}, longNumber = ${instance.longNumber}"
                )
            }
        }

        urlShouldBeHandled("/?text=abc&number=1&longNumber=2", "text = abc, number = 1, longNumber = 2")

        // missing parameter text
        handleRequest(HttpMethod.Get, "/?number=1&longNumber=2").let { call ->
            // null because missing parameter leads to routing miss
            assertEquals(HttpStatusCode.NotFound, call.response.status())
        }

        // illegal value for numeric property
        handleRequest(HttpMethod.Get, "/?text=abc&number=z&longNumber=2").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }

        // illegal value for numeric property
        handleRequest(HttpMethod.Get, "/?text=abc&number=${Long.MAX_VALUE}&longNumber=2").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLocationOrNull() {
        withLocationsApplication {
            application.routing {
                get<index> { index ->
                    assertSame(index, call.locationOrNull())
                    assertSame(index, call.location())
                    call.respondText("OK")
                }
                get("/no-location") {
                    assertFails {
                        assertNull(call.locationOrNull<index>())
                    }
                    assertFails {
                        assertNull(call.location<index>())
                    }
                    call.respondText("OK")
                }
            }

            urlShouldBeHandled("/", "OK")
            urlShouldBeHandled("/no-location", "OK")
        }
    }
}
