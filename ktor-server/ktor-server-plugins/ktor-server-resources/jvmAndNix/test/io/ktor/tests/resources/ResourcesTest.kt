/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import kotlin.test.*

internal fun withResourcesApplication(test: TestApplicationEngine.() -> Unit) = withTestApplication {
    application.install(Resources)
    test()
}

class ResourcesTest {
    @Serializable
    @Resource("/")
    class index

    @Test
    fun resourceWithoutURL() = withResourcesApplication {
        val href = application.href(index())
        assertEquals("/", href)
        application.routing {
            get<index> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/index")
    }

    @Test
    fun resourceLocal() {
        @Serializable
        @Resource("/")
        class indexLocal
        withResourcesApplication {
            val href = application.href(indexLocal())
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

    @Serializable
    @Resource("/about")
    class about

    @Test
    fun resourceWithURL() = withResourcesApplication {
        val href = application.href(about())
        assertEquals("/about", href)
        application.routing {
            get<about> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/about/123")
    }

    @Serializable
    @Resource("/user/{id}")
    class user(val id: Int)

    @Test
    fun resourceWithPathParam() = withResourcesApplication {
        val href = application.href(user(123))
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

    @Serializable
    @Resource("/user/{id}/{name}")
    class named(val id: Int, val name: String)

    @Test
    fun resourceWithUrlencodedPathParam() = withResourcesApplication {
        val href = application.href(named(123, "abc def"))
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

    @Serializable
    @Resource("/favorite")
    class favorite(val id: Int)

    @Test
    fun resourceWithQueryParam() = withResourcesApplication {
        val href = application.href(favorite(123))
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

    @Serializable
    @Resource("/container/{id}")
    class pathContainer(val id: Int) {
        @Serializable
        @Resource("/items")
        class items(val container: pathContainer)
    }

    @Test
    fun resourceWithPathParameterAndNestedData() = withResourcesApplication {
        val c = pathContainer(123)
        val href = application.href(pathContainer.items(c))
        assertEquals("/container/123/items", href)
        application.routing {
            get<pathContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/items?id=123")
    }

    @Serializable
    @Resource("/container")
    class queryContainer(val id: Int) {
        @Serializable
        @Resource("/items")
        class items(val container: queryContainer)
    }

    @Test
    fun resourceWithQueryParameterAndNestedData() = withResourcesApplication {
        val c = queryContainer(123)
        val href = application.href(queryContainer.items(c))
        assertEquals("/container/items?id=123", href)
        application.routing {
            get<queryContainer.items> { items ->
                assertEquals(123, items.container.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/container/items")
        urlShouldBeUnhandled("/container/123/items")
    }

    @Serializable
    @Resource("/container")
    class optionalName(val id: Int, val optional: String? = null)

    @Test
    fun resourceWithMissingOptionalStringParameter() = withResourcesApplication {
        val href = application.href(optionalName(123))
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

    @Serializable
    @Resource("/container")
    class optionalIndex(val id: Int, val optional: Int = 42)

    @Test
    fun resourceWithMissingOptionalIntParameter() = withResourcesApplication {
        val href = application.href(optionalIndex(123))
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

    @Test
    fun resourceWithSpecifiedOptionalQueryParameter() = withResourcesApplication {
        val href = application.href(optionalName(123, "text"))
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

    @Serializable
    @Resource("/container/{id?}")
    class optionalContainer(val id: Int? = null) {
        @Serializable
        @Resource("/items")
        class items(val parent: optionalContainer, val optional: String? = null)
    }

    @Test
    fun resourceWithOptionalPathAndQueryParameter() = withResourcesApplication {
        val href = application.href(optionalContainer())
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

    @Serializable
    @Resource("/container")
    class simpleContainer {
        @Serializable
        @Resource("/items")
        class items(val parent: simpleContainer)
    }

    @Test
    fun resourceWithSimplePathContainerAndItems() = withResourcesApplication {
        val href = application.href(simpleContainer.items(simpleContainer()))
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

    @Serializable
    @Resource("/container/{path...}")
    class tailCard(val path: List<String>)

    @Test
    fun resourceWithTailcard() = withResourcesApplication {
        val href = application.href(tailCard(emptyList()))
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

    @Serializable
    @Resource("/")
    class multiquery(val value: List<Int>)

    @Serializable
    @Resource("/")
    class multiquery2(val name: List<String>)

    @Test
    fun `resource with multiple query values`() = withResourcesApplication {
        val href = application.href(multiquery(listOf(1, 2, 3)))
        assertEquals("/?value=1&value=2&value=3", href)
        application.routing {
            get<multiquery> {
                call.respond(it.value.toString())
            }
        }
        urlShouldBeHandled(href, "[1, 2, 3]")
    }

    @Test
    fun resourceWithMultipleQueryValuesCanSelectByQueryParams() = withResourcesApplication {
        val href = application.href(multiquery(listOf(1)))
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

    @Test
    fun resourceWithMultipleQueryValuesCanSelectByQueryParams2() = withResourcesApplication {
        val href = application.href(multiquery2(listOf("john, mary")))
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

    @Serializable
    @Resource("/")
    class multiqueryWithDefault(val value: List<Int> = emptyList())

    @Test
    fun resourceWithMultipleQueryValuesAndDefault() = withResourcesApplication {
        val href = application.href(multiqueryWithDefault(listOf()))
        assertEquals("/", href)
        application.routing {
            get<multiqueryWithDefault> {
                call.respond(it.value.toString())
            }
        }
        urlShouldBeHandled(href, "[]")
    }

    @Serializable
    @Resource("/")
    class root

    @Test
    fun resourceRootByClass() = withResourcesApplication {
        val href = application.href(root())
        assertEquals("/", href)
        application.routing {
            get<root> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/index")
    }

    @Serializable
    @Resource("/help")
    class help

    @Test
    fun resourceByClass() = withResourcesApplication {
        val href = application.href(help())
        assertEquals("/help", href)
        application.routing {
            get<help> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/help/123")
    }

    @Serializable
    @Resource("/users")
    class users {
        @Serializable
        @Resource("/me")
        class me(val parent: users)

        @Serializable
        @Resource("/{id}")
        class user(val parent: users, val id: Int)
    }

    @Test
    fun resourceByClassInClass() = withResourcesApplication {
        val hrefMe = application.href(users.me(users()))
        assertEquals("/users/me", hrefMe)
        application.routing {
            get<users.me> {
                call.respond(HttpStatusCode.OK)
            }

            get<users.user> { user ->
                assertEquals(123, user.id)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(hrefMe)
        urlShouldBeUnhandled("/users/123")

        val hrefUsers = application.href(users.user(users(), 123))
        assertEquals("/users/123", hrefUsers)
        urlShouldBeHandled(hrefUsers)
        urlShouldBeUnhandled("/users/me")
    }

    @Serializable
    @Resource("/items/{id}")
    class items

    @Test
    fun resourceByClassHasBindArgument() {
        assertFailsWith<IllegalArgumentException> {
            withResourcesApplication {
                application.href(items)
            }
        }
    }

    @Serializable
    @Resource("/items/{itemId}/{extra?}")
    class OverlappingPath1(val itemId: Int, val extra: String?)

    @Serializable
    @Resource("/items/{extra}")
    class OverlappingPath2(val extra: String)

    @Test
    fun overlappingPathsAreResolvedAsExpected() = withResourcesApplication {
        application.routing {
            get<OverlappingPath1> {
                call.respond(HttpStatusCode.OK)
            }
            get<OverlappingPath2> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(application.href(OverlappingPath1(1, "Foo")))
        urlShouldBeUnhandled(application.href(OverlappingPath2("1-Foo")))
    }

    enum class resourceEnum {
        A, B, C
    }

    @Serializable
    @Resource("/")
    class resourceWithEnum(val e: resourceEnum)

    @Test
    fun resourceClassWithEnumValue() = withResourcesApplication {
        application.routing {
            get<resourceWithEnum> {
                call.respondText(it.e.name)
            }
        }

        urlShouldBeHandled("/?e=A", "A")
        urlShouldBeHandled("/?e=B", "B")

        handleRequest(HttpMethod.Get, "/?e=x").let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    fun resourceParameterMismatchShouldLeadToBadRequestStatus() = withResourcesApplication {
        @Serializable
        @Resource("/")
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
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
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
}
