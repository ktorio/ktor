/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.serialization

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

abstract class AbstractSerializationTest {
    protected val serializer = TestEntity.serializer()

    protected abstract val testContentType: ContentType
    protected abstract fun ContentNegotiation.Configuration.configureContentNegotiation()

    protected abstract fun simpleSerialize(any: TestEntity): ByteArray
    protected abstract fun simpleDeserialize(t: ByteArray): TestEntity

    protected fun withTestSerializingApplication(block: suspend TestApplicationEngine.() -> Unit): Unit =
        withTestApplication {
            application.install(ContentNegotiation) {
                configureContentNegotiation()
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
            }

            runBlocking {
                block()
            }
        }

    @Test
    fun testDumpNoAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump").let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    fun testDumpWithAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump") {
            addHeader(HttpHeaders.Accept, testContentType.toString())
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    fun testDumpWithMultipleAccept(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Get, "/dump") {
            addHeader(HttpHeaders.Accept, "$testContentType;q=1,text/plain;q=0.9")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            val bytes = call.response.byteContent
            assertNotNull(bytes)
            val entity = simpleDeserialize(bytes)
            assertEquals(777, entity.x)
        }
    }

    @Test
    fun testParseWithContentType(): Unit = withTestSerializingApplication {
        handleRequest(HttpMethod.Put, "/parse") {
            addHeader(HttpHeaders.ContentType, testContentType.toString())
            setBody(simpleSerialize(TestEntity(999)))
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals("OK", call.response.content)
        }
    }

    @Serializable
    data class TestEntity(val x: Int)
}
