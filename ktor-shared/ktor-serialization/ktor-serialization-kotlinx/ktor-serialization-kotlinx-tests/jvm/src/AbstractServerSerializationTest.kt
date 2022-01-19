/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.serialization.kotlinx.test

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

@Suppress("DEPRECATION")
public abstract class AbstractServerSerializationTest {
    protected val serializer: KSerializer<TestEntity> = TestEntity.serializer()

    protected abstract val defaultContentType: ContentType
    protected abstract val customContentType: ContentType
    protected abstract fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType)

    protected abstract fun simpleSerialize(any: TestEntity): ByteArray
    protected abstract fun simpleDeserialize(t: ByteArray): TestEntity

    protected fun withTestSerializingApplication(block: suspend TestApplicationEngine.() -> Unit): Unit =
        withTestApplication {
            application.install(ContentNegotiation) {
                configureContentNegotiation(defaultContentType)
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
    public data class TestEntity(val x: Int)
}
