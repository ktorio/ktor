/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING")

package io.ktor.client.features.cbor.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.features.cbor.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlin.test.*

/** Base class for CBOR tests. */
@Suppress("KDocMissingDocumentation")
abstract class CborTest : TestWithKtor() {
    private val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))
    private val users = listOf(
        User("martynas", 26),
        User("foo", 45)
    )

    @OptIn(ExperimentalSerializationApi::class)
    override val server: ApplicationEngine = embeddedServer(io.ktor.server.cio.CIO, serverPort) {
        install(ContentNegotiation) {
            serialization(ContentType.Application.Cbor, Cbor)
            serialization(customContentType, Cbor)
        }
        routing {
            createRoutes(this)
        }
    }

    private val customContentType = ContentType.parse("application/x-cbor")

    protected open fun createRoutes(routing: Routing): Unit = with(routing) {
        post("/widget") {
            val received = call.receive<Widget>()
            assertEquals(widget, received)
            call.respond(widget)
        }
        get("/users") {
            call.respond(Response(true, users))
        }
        get("/users-x") { // route for testing custom content type, namely "application/x-cbor"
            call.respond(Response(true, users))
        }
        post("/post-x") {
            require(call.request.contentType().withoutParameters() == customContentType) {
                "Request body content type should be $customContentType"
            }

            val requestPayload = call.receive<User>()
            call.respondText(requestPayload.toString())
        }
    }

    protected abstract val serializerImpl: CborSerializer?

    protected fun TestClientBuilder<*>.configClient() {
        config {
            configCborFeature()
        }
    }

    private fun HttpClientConfig<*>.configCborFeature(block: CborFeature.Config.() -> Unit = {}) {
        install(CborFeature) {
            serializerImpl?.let {
                serializer = it
            }
            block()
        }
    }

    private fun TestClientBuilder<*>.configCustomContentTypeClient(block: CborFeature.Config.() -> Unit) {
        config {
            configCborFeature(block)
        }
    }

    @org.junit.Test
    fun testEmptyBody(): Unit = testWithEngine(MockEngine) {
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
                contentType(ContentType.Application.Cbor)
            }
            configCborFeature()
        }

        test { client ->
            val response: HttpResponse = client.get("https://test.com")
            assertEquals("", response.readText())
            assertEquals("null", response.headers["X-ContentType"])
        }
    }

    @Test
    fun testSerializeSimple(): Unit = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.post<Widget>(body = widget, path = "/widget", port = serverPort) {
                contentType(ContentType.Application.Cbor)
            }

            assertEquals(widget, result)
        }
    }

    @Test
    fun testSerializeNested(): Unit = testWithEngine(CIO) {
        configClient()

        test { client ->
            val result = client.get<Response<List<User>>>(path = "/users", port = serverPort)

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    fun testCustomContentTypes(): Unit = testWithEngine(CIO) {
        configCustomContentTypeClient {
            acceptContentTypes = listOf(customContentType)
        }

        test { client ->
            val result = client.get<Response<List<User>>>(path = "/users-x", port = serverPort)

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }

        test { client ->
            client.get<HttpStatement>(path = "/users-x", port = serverPort).execute { response ->
                val result = response.receive<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                assertEquals(customContentType, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name1", 99)

            val result = client.post<String>(path = "/post-x", port = serverPort) {
                body = payload
                contentType(customContentType)
            }

            assertEquals(payload.toString(), result)
        }
    }

    @Test
    fun testCustomContentTypesMultiple(): Unit = testWithEngine(CIO) {
        configCustomContentTypeClient {
            acceptContentTypes = listOf(ContentType.Application.Cbor, customContentType)
        }

        test { client ->
            val payload = User("name2", 98)

            val result = client.post<String>(path = "/post-x", port = serverPort) {
                body = payload
                contentType(customContentType)
            }

            assertEquals(payload.toString(), result)
        }
    }

    @Test
    fun testCustomContentTypesWildcard(): Unit = testWithEngine(CIO) {
        configCustomContentTypeClient {
            acceptContentTypes = listOf(ContentType.Application.Any)
        }

        test { client ->
            client.get<HttpStatement>(path = "/users-x", port = serverPort).execute { response ->
                val result = response.receive<Response<List<User>>>()

                assertTrue(result.ok)
                assertNotNull(result.result)
                assertEquals(users, result.result)

                // cbor is registered first on server so it should win
                // since Accept header consist of the wildcard
                assertEquals(ContentType.Application.Cbor, response.contentType()?.withoutParameters())
            }
        }

        test { client ->
            val payload = User("name3", 97)

            val result = client.post<String>(path = "/post-x", port = serverPort) {
                body = payload
                contentType(customContentType) // custom content type should match the wildcard
            }

            assertEquals(payload.toString(), result)
        }
    }

    @Serializable
    data class Response<T>(
        val ok: Boolean,
        val result: T?
    )

    @Serializable
    data class Widget(
        val name: String,
        val value: Int,
        val tags: List<String> = emptyList()
    )

    @Serializable
    data class User(
        val name: String,
        val age: Int
    )
}
