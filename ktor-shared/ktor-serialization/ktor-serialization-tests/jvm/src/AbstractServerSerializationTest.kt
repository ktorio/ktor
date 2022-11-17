/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.serialization.test

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import java.nio.charset.*
import kotlin.test.*

@Suppress("DEPRECATION")
public abstract class AbstractServerSerializationTest {
    protected abstract val defaultContentType: ContentType
    protected abstract val customContentType: ContentType
    protected abstract fun ContentNegotiationConfig.configureContentNegotiation(
        contentType: ContentType,
        streamRequestBody: Boolean,
        prettyPrint: Boolean
    )

    protected abstract fun simpleSerialize(any: TestEntity): ByteArray
    protected abstract fun simpleDeserialize(t: ByteArray): TestEntity
    protected abstract fun simpleDeserializeList(
        t: ByteArray,
        charset: Charset = Charsets.UTF_8,
        prettyPrint: Boolean
    ): List<TestEntity>

    private fun withTestSerializingApplication(
        streamRequestBody: Boolean = true,
        prettyPrint: Boolean = false,
        block: suspend TestApplicationEngine.() -> Unit
    ): Unit =
        withTestApplication {
            application.install(ContentNegotiation) {
                configureContentNegotiation(defaultContentType, streamRequestBody, prettyPrint)
            }

            application.routing {
                get("/dump") {
                    call.respond(TestEntity(777))
                }
                put("/parse") {
                    val entity = call.receive<TestEntity>()
                    assertEquals(999, entity.x)
                    call.respondText("OK")
                }
                get("/list") {
                    call.respond(testEntities)
                }
                get("/flow") {
                    call.respond(testEntities.asFlow())
                }
            }

            runBlocking {
                block()
            }
        }

    @Test
    public fun testDumpNoAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    public fun testDumpWithAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump") {
            addHeader(HttpHeaders.Accept, defaultContentType.toString())
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    public fun testDumpWithMultipleAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump") {
            addHeader(HttpHeaders.Accept, "$defaultContentType;q=1,text/plain;q=0.9")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    public fun testListNoAcceptUtf8(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/list").let { call -> verifyListResponse(call.response, Charsets.UTF_8) }
    }

    @Test
    public fun testListNoAcceptUtf16(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/list") {
            addHeader("Accept-Charset", "UTF-16")
        }.let { call -> verifyListResponse(call.response, Charsets.UTF_16) }
    }

    @Test
    public fun testFlowNoAcceptUtf8(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/flow").let { call -> verifyListResponse(call.response, Charsets.UTF_8) }
    }

    @Test
    public fun testFlowNoAcceptUtf8NoStreamRequestBody(): Unit = withTestSerializingApplication(false) {
        handleRequest(HttpMethod.Get, "/flow").let { call -> verifyListResponse(call.response, Charsets.UTF_8) }
    }

    @Test
    public fun testFlowNoAcceptUtf8PrettyPrint(): Unit = withTestSerializingApplication(prettyPrint = true) {
        handleRequest(HttpMethod.Get, "/flow").let { call -> verifyListResponse(call.response, Charsets.UTF_8, true) }
    }

    @Test
    public fun testFlowNoAcceptUtf16(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/flow") {
            addHeader("Accept-Charset", "UTF-16")
        }.let { call -> verifyListResponse(call.response, Charsets.UTF_16) }
    }

    @Test
    public fun testFlowNoAcceptUtf16NoStreamRequestBody(): Unit =
        withTestSerializingApplication(false) {
            handleRequest(HttpMethod.Get, "/flow") {
                addHeader("Accept-Charset", "UTF-16")
            }.let { call -> verifyListResponse(call.response, Charsets.UTF_16) }
        }

    private fun verifyListResponse(response: TestApplicationResponse, charset: Charset, prettyPrint: Boolean = false) {
        assertEquals(HttpStatusCode.OK, response.status())
        val bytes = response.byteContent
        assertNotNull(bytes)
        val listEntity = simpleDeserializeList(bytes, charset, prettyPrint)
        assertEquals(testEntities, listEntity)
    }

    @Test
    public fun testParseWithContentType(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Put, "/parse") {
            addHeader(HttpHeaders.ContentType, defaultContentType.toString())
            setBody(simpleSerialize(TestEntity(999)))
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("OK", call.response.content)
        }
    }

    @Serializable
    public data class TestEntity(val x: Int, val y: String? = null)

    private companion object {
        private val testEntities = listOf(TestEntity(111, "ൠ"), TestEntity(222))
    }
}
