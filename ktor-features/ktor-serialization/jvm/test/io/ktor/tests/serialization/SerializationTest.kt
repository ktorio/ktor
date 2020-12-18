/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.serialization

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.*
import kotlin.test.*

class SerializationTest {

    @Test
    @Ignore
    fun testMap(): Unit = withTestApplication {
        val uc = "\u0422"

        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
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
    fun testEntityListReceive(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            post("/") {
                val receivedList = call.receive<List<MyEntity>>()
                call.respond(receivedList.toString())
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""[{"id":1,"name":"Hello, World!","children":[]}]""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[MyEntity(id=1, name=Hello, World!, children=[])]"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntityListSend(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            get("/") {
                call.respond(listOf(MyEntity(3, "third", emptyList())))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[{"id":3,"name":"third","children":[]}]"""), response.content!!.lines())
        }
    }

    @Test
    fun testEntityArrayReceive(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            post("/") {
                val receivedList = call.receive<Array<MyEntity>>()
                call.respond(receivedList.toList().toString())
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""[{"id":1,"name":"Hello, World!","children":[]}]""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[MyEntity(id=1, name=Hello, World!, children=[])]"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntityArraySend(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            get("/") {
                call.respond(arrayOf(MyEntity(1, "first", emptyList())))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[{"id":1,"name":"first","children":[]}]"""), response.content!!.lines())
        }
    }

    @Test
    fun testEntitySetReceive(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            post("/") {
                val receivedList = call.receive<Set<MyEntity>>()
                call.respond(receivedList.toString())
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""[{"id":1,"name":"Hello, World!","children":[]}]""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[MyEntity(id=1, name=Hello, World!, children=[])]"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntitySetSend(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            get("/") {
                call.respond(setOf(MyEntity(2, "second", emptyList())))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""[{"id":2,"name":"second","children":[]}]"""), response.content!!.lines())
        }
    }

    @Test
    fun testEntityMapReceive(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            post("/") {
                val receivedList = call.receive<Map<Int, MyEntity>>()
                call.respond(receivedList.toString())
            }
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""{"1":{"id":1,"name":"Hello, World!","children":[]}}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{1=MyEntity(id=1, name=Hello, World!, children=[])}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntityMapSend(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }
        application.routing {
            get("/") {
                call.respond(mapOf(3 to MyEntity(3, "third", emptyList())))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"3":{"id":3,"name":"third","children":[]}}"""), response.content!!.lines())
        }
    }

    @Test
    fun testEntity(): Unit = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter())
        }

        application.routing {
            val model = MyEntity(
                777, "Cargo",
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
                listOf("""{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},{"item":"Sphere","quantity":2},{"item":"$uc","quantity":3}]}"""),
                response.content!!.lines()
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody("""{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},{"item":"Sphere","quantity":2},{"item":"$uc", "quantity":3}]}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(
                listOf("""MyEntity(id=777, name=Cargo, children=[ChildEntity(item=Qube, quantity=1), ChildEntity(item=Sphere, quantity=2), ChildEntity(item=$uc, quantity=3)])"""),
                response.content!!.lines()
            )
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

    }

    @Serializable
    private data class TextPlainData(val x: Int)

    @Test
    fun testOnTextAny(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            json()
            register(contentType = ContentType.Text.Any, converter = SerializationConverter())
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

    @Serializable
    private class NullValues

    @Test
    fun testReceiveNullValue(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            json()
            register(contentType = ContentType.Text.Any, converter = SerializationConverter())
        }

        application.routing {
            post("/") {
                val result = try {
                    call.receive<NullValues>().toString()
                } catch (expected: SerializationException) {
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
    fun testJsonElements(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            json()
        }
        application.routing {
            get("/map") {
                call.respond(buildJsonObject {
                    put("a", "1")
                    put("b", buildJsonObject {
                        put("c", 3)
                    })
                    put("x", JsonNull)
                })
            }
            get("/array") {
                call.respond(buildJsonObject {
                    put("a", "1")
                    put("b", buildJsonArray {
                        add("c")
                        add(JsonPrimitive(2))
                    })
                })
            }
        }

        handleRequest(HttpMethod.Get, "/map").let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("""{"a":"1","b":{"c":3},"x":null}""", it.response.content)
        }
        handleRequest(HttpMethod.Get, "/array").let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("""{"a":"1","b":["c",2]}""", it.response.content)
        }
    }

    @Test
    fun testMapsElements(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            json()
        }
        application.routing {
            get("/map") {
                call.respond(
                    mapOf(
                        "a" to "1",
                        null to "2",
                        "b" to null
                    )
                )
            }
        }

        handleRequest(HttpMethod.Get, "/map").let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("""{"a":"1",null:"2","b":null}""", it.response.content)
        }
    }

    @Test
    fun testRespondPolymorphic(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter(Json))
        }
        application.routing {
            get("/sealed") {
                call.respond(listOf(TestSealed.A("valueA"), TestSealed.B("valueB")))
            }
        }

        handleRequest(HttpMethod.Get, "/sealed") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(
                """[{"type":"io.ktor.tests.serialization.TestSealed.A","valueA":"valueA"},{"type":"io.ktor.tests.serialization.TestSealed.B","valueB":"valueB"}]""",
                call.response.content
            )
        }
    }

    @Test
    fun testRespondAny(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter(Json))
        }
        application.routing {
            get("/") {
                call.respond(listOf(TextPlainData(777), TextPlainData(888)) as Any)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(
                """[{"x":777},{"x":888}]""",
                call.response.content
            )
        }
    }

    @Test
    fun testRespondDifferentRuntimeTypes(): Unit = withTestApplication {
        var counter = 0
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, SerializationConverter(Json))
        }
        application.routing {
            get("/") {
                call.respond(
                    when (counter) {
                        0 -> TextPlainData(777)
                        1 -> TestSealed.A("A")
                        2 -> TestSealed.B("B")
                        else -> HttpStatusCode.Accepted
                    }
                )
                counter++
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("""{"x":777}""", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("""{"valueA":"A"}""", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("""{"valueB":"B"}""", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.Accepted, call.response.status())
            assertNull(call.response.content)
        }
    }

    @Test
    fun testGeneric(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            json()
        }
        application.routing {
            get("/generic") {
                call.respond(GenericEntity(id = 1, data = "asd"))
            }
        }

        handleRequest(HttpMethod.Get, "/generic").let {
            assertEquals(HttpStatusCode.OK, it.response.status())
            assertEquals("""{"id":1,"data":"asd"}""", it.response.content)
        }
    }
}

@Serializable
data class GenericEntity<T>(val id: Int, val data: T)

@Serializable
data class MyEntity(val id: Int, val name: String, val children: List<ChildEntity>)

@Serializable
data class ChildEntity(val item: String, val quantity: Int)

@Serializable
sealed class TestSealed {
    @Serializable
    data class A(val valueA: String) : TestSealed()

    @Serializable
    data class B(val valueB: String) : TestSealed()
}

private fun SerializationConverter(): SerializationConverter =
    SerializationConverter(DefaultJson)
