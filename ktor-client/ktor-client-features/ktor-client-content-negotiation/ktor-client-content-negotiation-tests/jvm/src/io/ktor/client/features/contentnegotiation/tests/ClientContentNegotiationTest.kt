/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING")

package io.ktor.client.features.contentnegotiation.tests

import com.fasterxml.jackson.annotation.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.shared.serialization.*
import kotlinx.serialization.*
import kotlin.test.*

/** Base class for JSON tests. */
@Suppress("KDocMissingDocumentation")
public abstract class ClientContentNegotiationTest : TestWithKtor() {
    private val widget = Widget("Foo", 1000, listOf("a", "b", "c"))
    private val users = listOf(
        User("x", 10),
        User("y", 45)
    )

    override val server: ApplicationEngine = embeddedServer(io.ktor.server.cio.CIO, serverPort) {
        routing {
            createRoutes(this)
        }
    }

    private val customContentType = ContentType.parse("application/x-json")

    protected open fun createRoutes(routing: Routing): Unit = with(routing) {
        post("/echo") {
            val received = call.receive<String>()
            println(received)
            call.respondText(received, call.request.contentType())
        }
        post("/widget") {
            val received = call.receive<String>()
            assertEquals("""{"name":"Foo","value":1000,"tags":["a","b","c"]}""", received)
            call.respondText(received, ContentType.Application.Json)
        }
        get("/users") {
            call.respondText(
                """{"ok":true,"result":[{"name":"x","age":10},{"name":"y","age":45}]}""",
                ContentType.Application.Json
            )
        }
        get("/users-x") { // route for testing custom content type, namely "application/x-json"
            call.respondText(
                """{"ok":true,"result":[{"name":"x","age":10},{"name":"y","age":45}]}""",
                customContentType
            )
        }
        post("/post-x") {
            require(call.request.contentType().withoutParameters() == customContentType) {
                "Request body content type should be $customContentType"
            }

            val requestPayload = call.receive<String>()
            call.respondText(requestPayload, ContentType.Application.Json)
        }
    }

    protected abstract val converter: ContentConverter?

    protected fun TestClientBuilder<*>.configClient() {
        config {
            configJsonFeature()
        }
    }

    private fun HttpClientConfig<*>.configJsonFeature(block: ContentNegotiation.Config.() -> Unit = {}) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, converter!!)
            block()
        }
    }

    private fun TestClientBuilder<*>.configCustomContentTypeClient(block: ContentNegotiation.Config.() -> Unit = {}) {
        config {
            configJsonFeature(block)
        }
    }

    @Test
    public fun testEmptyBody() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    respond(
                        request.body.toByteReadPacket().readText(),
                        headers = buildHeaders {
                            append("X-ContentType", request.body.contentType.toString())
                        }
                    )
                }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            configJsonFeature()
        }

        test { client ->
            val response: HttpResponse = client.get("https://test.com")
            assertEquals("", response.bodyAsText())
            assertEquals("null", response.headers["X-ContentType"])
        }
    }

    @Test
    public fun testSerializeSimple() = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.post {
                setBody(widget)
                url(path = "/widget", port = serverPort)
                contentType(ContentType.Application.Json)
            }.body<Widget>()

            assertEquals(widget, result)
        }
    }

    @Test
    public fun testSerializeNested() = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.get { url(path = "/users", port = serverPort) }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    public fun testCustomContentTypes() = testWithEngine(CIO) {
        configCustomContentTypeClient {
            register(customContentType, converter!!)
        }

        test { client ->
            val result = client.get { url(path = "/users-x", port = serverPort) }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }

        test { client ->
            client.prepareGet { url(path = "/users-x", port = serverPort) }.execute { response ->
                val result = response.body<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                assertEquals(customContentType, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name1", 99)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType)
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    public fun testCustomContentTypesMultiple() = testWithEngine(CIO) {
        configCustomContentTypeClient {
            register(customContentType, converter!!)
        }

        test { client ->
            val payload = User("name2", 98)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType)
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    public fun testCustomContentTypesWildcard() = testWithEngine(CIO) {
        configCustomContentTypeClient {
            register(customContentType, converter!!)
        }

        test { client ->
            client.prepareGet { url(path = "/users-x", port = serverPort) }.execute { response ->
                val result = response.body<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                // json is registered first on server so it should win
                // since Accept header consist of the wildcard
                assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name3", 97)

            val result = client.post {
                url(path = "/post-x", port = serverPort)
                setBody(payload)
                contentType(customContentType) // custom content type should match the wildcard
            }.body<User>()

            assertEquals(payload, result)
        }
    }

    @Test
    public fun testGeneric() = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.post {
                url(path = "/echo", port = serverPort)
                contentType(ContentType.Application.Json)
                setBody(Response(true, users))
            }.body<Response<List<User>>>()

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    public open fun testSealed() = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.post {
                url(path = "/echo", port = serverPort)
                contentType(ContentType.Application.Json)
                setBody(listOf(TestSealed.A("A"), TestSealed.B("B")))
            }.body<List<TestSealed>>()

            assertEquals(listOf(TestSealed.A("A"), TestSealed.B("B")), result)
        }
    }

    @Serializable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed class TestSealed {
        @Serializable
        data class A(val valueA: String) : TestSealed()

        @Serializable
        data class B(val valueB: String) : TestSealed()
    }

    @Serializable
    public data class Response<T>(
        val ok: Boolean,
        val result: T?
    )

    @Serializable
    public data class Widget(
        val name: String,
        val value: Int,
        val tags: List<String> = emptyList()
    )

    @Serializable
    public data class User(
        val name: String,
        val age: Int
    )
}
