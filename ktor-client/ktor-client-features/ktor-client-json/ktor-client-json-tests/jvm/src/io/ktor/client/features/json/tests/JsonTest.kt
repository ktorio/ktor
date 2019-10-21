/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.tests

import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.serialization.*
import kotlin.io.use
import kotlin.test.*

/** Base class for JSON tests. */
@Suppress("KDocMissingDocumentation")
abstract class JsonTest : TestWithKtor() {
    val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))
    val users = listOf(
        User("vasya", 10),
        User("foo", 45)
    )

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        install(ContentNegotiation) {
            gson()
            gson(customContentType)
        }
        routing {
            createRoutes(this)
        }
    }

    val customContentType = ContentType.parse("application/x-json")

    protected open fun createRoutes(routing: Routing): Unit = with(routing) {
        post("/widget") {
            val received = call.receive<Widget>()
            assertEquals(widget, received)
            call.respond(widget)
        }
        get("/users") {
            call.respond(Response(true, users))
        }
        get("/users-x") { // route for testing custom content type, namely "application/x-json"
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

    protected abstract val serializerImpl: JsonSerializer?

    protected fun TestClientBuilder<*>.configClient() {
        config {
            install(JsonFeature) {
                serializer = serializerImpl
            }
        }
    }

    private fun TestClientBuilder<*>.configCustomContentTypeClient(block: JsonFeature.Config.() -> Unit) {
        config {
            install(JsonFeature) {
                serializer = serializerImpl
                block()
            }
        }
    }

    @Test
    fun testSerializeSimple() = clientTest(CIO) {
        configClient()

        test { client ->
            val result = client.post<Widget>(body = widget, path = "/widget", port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(widget, result)
        }
    }

    @Test
    fun testSerializeNested() = clientTest(CIO) {
        configClient()

        test { client ->
            val result = client.get<Response<List<User>>>(path = "/users", port = serverPort)

            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }

    @Test
    fun testCustomContentTypes() = clientTest(CIO) {
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
    fun testCustomContentTypesMultiple() = clientTest(CIO) {
        configCustomContentTypeClient {
            acceptContentTypes = listOf(ContentType.Application.Json, customContentType)
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
    fun testCustomContentTypesWildcard() = clientTest(CIO) {
        configCustomContentTypeClient {
            acceptContentTypes = listOf(ContentType.Application.Any)
        }

        test { client ->
            client.get<HttpStatement>(path = "/users-x", port = serverPort).execute { response ->
                val result = response.receive<Response<List<User>>>()

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
