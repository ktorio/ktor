/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.test

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import java.nio.charset.*
import kotlin.test.*

public abstract class AbstractServerSerializationTest {
    private val uc = "\u0422"

    protected abstract val defaultContentType: ContentType
    protected abstract val customContentType: ContentType
    protected abstract fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType)

    protected abstract fun simpleSerialize(any: MyEntity): ByteArray
    protected abstract fun simpleDeserialize(t: ByteArray): MyEntity
    protected abstract fun simpleDeserializeList(t: ByteArray, charset: Charset = Charsets.UTF_8): List<MyEntity>

    private fun withTestSerializingApplication(
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        install(ContentNegotiation) {
            configureContentNegotiation(defaultContentType)
        }

        routing {
            get("/list") {
                call.respond(testEntities)
            }
            get("/flow") {
                call.respond(testEntities.asFlow())
            }
            get("/map") {
                val model = mapOf("id" to 1, "title" to "Hello, World!", "unicode" to uc)
                call.respond(model)
            }
            post("/map") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
            get("/entity") {
                val model = MyEntity(
                    777,
                    "Cargo",
                    listOf(ChildEntity("Qube", 1), ChildEntity("Sphere", 2), ChildEntity(uc, 3))
                )
                call.respond(model)
            }
            post("/entity") {
                val entity = call.receive<MyEntity>()
                call.respond(entity)
            }
            post("/null") {
                val result = call.receiveNullable<NullValues?>() ?: "OK"
                call.respondText(result.toString())
            }
        }

        block()
    }

    @Test
    public fun testEntityNoAccept(): Unit = withTestSerializingApplication {
        client.get("/entity").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val bytes = response.bodyAsBytes()
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testEntityWithAccept(): Unit = withTestSerializingApplication {
        client.get("/entity") {
            header(HttpHeaders.Accept, defaultContentType.toString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val bytes = response.bodyAsBytes()
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testDumpWithMultipleAccept(): Unit = withTestSerializingApplication {
        client.get("/entity") {
            header(HttpHeaders.Accept, "$defaultContentType;q=1,text/plain;q=0.9")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val bytes = response.bodyAsBytes()
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.id)
        }
    }

    @Test
    public fun testListNoAcceptUtf8(): Unit = withTestSerializingApplication {
        client.get("/list").let { response -> verifyListResponse(response, Charsets.UTF_8) }
    }

    @Test
    public fun testListAcceptUtf16(): Unit = withTestSerializingApplication {
        client.get("/list") {
            header(HttpHeaders.AcceptCharset, "UTF-16")
        }.let { response -> verifyListResponse(response, Charsets.UTF_16) }
    }

    @Test
    public open fun testFlowNoAcceptUtf8(): Unit = withTestSerializingApplication {
        client.get("/flow").let { response ->
            verifyListResponse(response, Charsets.UTF_8)
            assertEquals("chunked", response.headers[HttpHeaders.TransferEncoding])
        }
    }

    @Test
    public open fun testFlowAcceptUtf16(): Unit = withTestSerializingApplication {
        client.get("/flow") {
            header(HttpHeaders.AcceptCharset, "UTF-16")
        }.let { response -> verifyListResponse(response, Charsets.UTF_16) }
    }

    private suspend fun verifyListResponse(response: HttpResponse, charset: Charset) {
        assertEquals(HttpStatusCode.OK, response.status)
        val bytes = response.bodyAsBytes()
        val listEntity = simpleDeserializeList(bytes, charset)
        assertEquals(testEntities, listEntity)
    }

    @Test
    public open fun testMap(): Unit = withTestSerializingApplication {
        client.get("/map") {
            header(HttpHeaders.Accept, "application/json")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""", response.bodyAsText())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        client.post("/map") {
            header(HttpHeaders.Accept, "text/plain")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":1.0,"title":"Hello, World!","unicode":"$uc"}""")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""id=1.0, title=Hello, World!, unicode=$uc""", response.bodyAsText())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    public fun testPostEntity(): Unit = withTestSerializingApplication {
        val body = testEntities.first()
        client.post("/entity") {
            header(HttpHeaders.ContentType, defaultContentType.toString())
            setBody(simpleSerialize(body))
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(body, simpleDeserialize(response.bodyAsBytes()))
        }
    }

    @Test
    public open fun testReceiveNullValue(): Unit = withTestSerializingApplication {
        client.post("/null") {
            header(HttpHeaders.ContentType, "application/json")
            setBody("null")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
        }
    }

    @Serializable
    private class NullValues

    @Serializable
    public data class MyEntity(val id: Int, val name: String, val children: List<ChildEntity>)

    @Serializable
    public data class ChildEntity(val item: String, val quantity: Int)

    private companion object {
        private val testEntities = listOf(
            MyEntity(111, "ൠ", listOf(ChildEntity("item1", 1), ChildEntity("item2", 2))),
            MyEntity(222, "ൠ1", listOf(ChildEntity("item3", 3), ChildEntity("item4", 5)))
        )
    }
}
