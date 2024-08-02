/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class ServerRequestContextTest {
    @Test
    fun testSimpleStringContent() {
        withTestApplication {
            server.intercept(ServerCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveText())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody("bodyContent")
            }
        }
    }

    @Test
    fun testSimpleStringContentWithBadContentType() {
        withTestApplication {
            server.intercept(ServerCallPipeline.Call) {
                assertFailsWith<BadRequestException> {
                    call.receiveText()
                }.let { throw it }
            }

            handleRequest(HttpMethod.Get, "") {
                addHeader(HttpHeaders.ContentType, "...la..la..la")
                setBody("any")
            }.let { call ->
                assertEquals(HttpStatusCode.BadRequest, call.response.status())
            }
        }
    }

    @Test
    fun testStringValues() {
        withTestApplication {
            val values = parametersOf("a", "1")

            server.intercept(ServerCallPipeline.Call) {
                val actual = call.receiveParameters()
                assertEquals(values, actual)
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                val value = values.formUrlEncode()
                setBody(value)
            }
        }
    }

    @Test
    fun testIllegalContentType() {
        withTestApplication {
            server.intercept(ServerCallPipeline.Call) {
                assertFailsWith<BadRequestException> {
                    call.receiveParameters()
                }.let { throw it }
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, "...la..la..la")
                setBody("don't care")
            }.let { call ->
                assertEquals(HttpStatusCode.BadRequest, call.response.status())
            }
        }
    }

    @Test
    fun testStringValuesWithCharset() {
        withTestApplication {
            val values = parametersOf("a", "1")

            server.intercept(ServerCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=UTF-8")
                setBody(values.formUrlEncode())
            }
        }
    }

    @Test
    fun testCustomTransform() {
        withTestApplication {
            val value = IntList(listOf(1, 2, 3, 4))

            server.receivePipeline.intercept(ServerReceivePipeline.Transform) { body ->
                if (call.receiveType != typeInfo<IntList>()) return@intercept
                val message = body as? ByteReadChannel ?: return@intercept

                val string = message.readRemaining().readText()
                val transformed = IntList.parse(string)
                proceedWith(transformed)
            }

            server.intercept(ServerCallPipeline.Call) {
                assertEquals(value, call.receive())
            }

            handleRequest(HttpMethod.Get, "") {
                setBody(value.toString())
            }
        }
    }

    @Test
    fun testFormUrlEncodedContent() {
        val values = parametersOf(
            "one" to listOf("1"),
            "two_space_three_and_four" to listOf("2 3 & 4")
        )
        withTestApplication {
            server.intercept(ServerCallPipeline.Call) {
                assertEquals(values, call.receiveParameters())
            }

            handleRequest(HttpMethod.Post, "") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(values.formUrlEncode())
            }
        }
    }

    @Test
    fun testReceiveUnsupportedTypeFailing(): Unit = withTestApplication {
        server.install(ContentNegotiation)

        server.routing {
            get("/") {
                val v = call.receive<IntList>()
                call.respondText(v.values.joinToString())
            }
        }

        assertEquals(415, handleRequest(HttpMethod.Get, "/").response.status()?.value)
    }

    @Test
    fun testReceiveUnsupportedTypeNotFailing(): Unit = withTestApplication {
        server.install(ContentNegotiation)

        server.routing {
            get("/") {
                val v = runCatching { call.receiveNullable<IntList>() }.getOrNull()
                call.respondText(v?.values?.joinToString() ?: "(none)")
            }
        }

        assertEquals(200, handleRequest(HttpMethod.Get, "/").response.status()?.value)
    }

    @Test
    fun testDoubleReceiveWithNoPlugin(): Unit = withTestApplication {
        server.intercept(ServerCallPipeline.Call) {
            assertEquals("bodyContent", call.receiveText())
            assertFailsWith<RequestAlreadyConsumedException> {
                call.receiveText()
            }
        }

        handleRequest(HttpMethod.Get, "") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveDifferentTypes(): Unit = withTestApplication {
        server.install(DoubleReceive)

        server.intercept(ServerCallPipeline.Call) {
            assertEquals("bodyContent".toByteArray().toList(), call.receive<ByteArray>().toList())
            assertEquals("bodyContent", call.receiveText())

            // this also works because we already have a byte array cached
            assertEquals("bodyContent", call.receiveChannel().readUTF8Line())
        }

        handleRequest(HttpMethod.Get, "") {
            setBody("bodyContent")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testDoubleReceiveChannels(): Unit = withTestApplication {
        server.install(DoubleReceive)

        server.intercept(ServerCallPipeline.Call) {
            call.receiveChannel().readRemaining().use { packet ->
                assertEquals(11, packet.remaining)
            }
            call.receiveChannel().readRemaining().use { packet ->
                assertEquals(11, packet.remaining)
            }
        }

        handleRequest(HttpMethod.Get, "") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveAfterTransformationFailed(): Unit = withTestApplication {
        server.install(DoubleReceive)

        server.receivePipeline.intercept(ServerReceivePipeline.Transform) {
            if (call.receiveType.type == IntList::class) {
                throw MySpecialException()
            }
        }
        server.intercept(ServerCallPipeline.Call) {
            assertFailsWith<MySpecialException> {
                call.receive<IntList>()
            }
            assertFailsWith<MySpecialException> {
                call.receive<IntList>()
            }
        }

        handleRequest(HttpMethod.Get, "") {
            setBody("bodyContent")
        }
    }
}

data class IntList(val values: List<Int>) {
    override fun toString() = "$values"

    companion object {
        fun parse(text: String) = IntList(text.removeSurrounding("[", "]").split(",").map { it.trim().toInt() })
    }
}

private class MySpecialException : Exception("Expected exception")
