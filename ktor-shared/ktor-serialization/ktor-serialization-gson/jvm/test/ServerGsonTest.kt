/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.google.gson.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class ServerGsonTest {
    @Test
    fun testMap() = withTestApplication {
        val uc = "\u0422"

        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
        }
        application.routing {
            val model = mapOf("id" to 1, "title" to "Hello, World!", "unicode" to uc)
            get("/") {
                call.respond(model)
            }
            post("/") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"id":1,"title":"Hello, World!","unicode":"$uc"}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""id=1.0, title=Hello, World!, unicode=$uc"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntity() = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
        }

        application.routing {
            val model = MyEntity(
                777,
                "Cargo",
                listOf(
                    ChildEntity("Qube", 1),
                    ChildEntity("Sphere", 2),
                    ChildEntity(uc, 3)
                )
            )

            get("/") {
                call.respond(model)
            }
            post("/") {
                val entity = call.receive<MyEntity>()
                call.respond(entity.toString())
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(
                listOf(
                    """{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},""" +
                        """{"item":"Sphere","quantity":2},{"item":"$uc","quantity":3}]}"""
                ),
                response.content!!.lines()
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(
                """{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},""" +
                    """{"item":"Sphere","quantity":2},{"item":"$uc", "quantity":3}]}"""
            )
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(
                listOf(
                    """MyEntity(id=777, name=Cargo, children=[ChildEntity(item=Qube, quantity=1), """ +
                        """ChildEntity(item=Sphere, quantity=2), ChildEntity(item=$uc, quantity=3)])"""
                ),
                response.content!!.lines()
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    private data class TextPlainData(val x: Int)

    @Test
    fun testGsonOnTextAny(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val instance = call.receive<TextPlainData>()
                assertEquals(TextPlainData(777), instance)
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "text/plain")
            setBody("{\"x\": 777}")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"x\": 777}")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }

    @Test
    fun testReceiveExcludedClass(): Unit = withTestApplication {
        data class Excluded(val x: Int)

        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val result = try {
                    call.receive<Excluded>().toString()
                } catch (expected: ExcludedTypeGsonException) {
                    "OK"
                }
                call.respondText(result)
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{\"x\": 777}")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }

    private class NullValues

    @Test
    fun testReceiveNullValue(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val result = try {
                    call.receive<NullValues>().toString()
                } catch (expected: ContentTransformationException) {
                    "OK"
                }
                call.respondText(result)
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("null")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }

    @Test
    fun testReceiveValuesMap() = withTestApplication {
        application.install(ContentNegotiation) {
            gson()
            register(contentType = ContentType.Text.Any, converter = GsonConverter())
        }

        application.routing {
            post("/") {
                val json = call.receive<JsonObject>()

                val expected = JsonObject().apply {
                    add(
                        "hello",
                        JsonObject().apply {
                            addProperty("ktor", "world")
                        }
                    )
                }

                assertEquals(expected, json)
                call.respondText("OK")
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("{ hello: { ktor : world } }")
        }.let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("OK", it.response.content)
        }
    }
}

data class MyEntity(val id: Int, val name: String, val children: List<ChildEntity>)
data class ChildEntity(val item: String, val quantity: Int)
