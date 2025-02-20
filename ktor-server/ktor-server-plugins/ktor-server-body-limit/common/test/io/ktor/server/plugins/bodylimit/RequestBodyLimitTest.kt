/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.bodylimit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class RequestBodyLimitTest {

    @Test
    fun testBodyLimitPerCall() = testApplication {
        install(RequestBodyLimit) {
            bodyLimit { call ->
                when {
                    call.request.uri.endsWith("a") -> 15
                    call.request.uri.endsWith("b") -> 20
                    else -> Long.MAX_VALUE
                }
            }
        }

        routing {
            post("{path}") {
                call.receive<ByteArray>()
                call.respond("OK: ${call.parameters["path"]}")
            }
        }

        val requestAOk = client.post("a") {
            setBody(ByteReadChannel(ByteArray(15)))
        }
        assertNull(requestAOk.request.headers[HttpHeaders.ContentLength])
        assertEquals(HttpStatusCode.OK, requestAOk.status)

        val requestALarge = client.post("a") {
            setBody(ByteReadChannel(ByteArray(16)))
        }
        assertNull(requestALarge.request.headers[HttpHeaders.ContentLength])
        assertEquals(HttpStatusCode.PayloadTooLarge, requestALarge.status)

        val requestBOk = client.post("b") {
            setBody(ByteReadChannel(ByteArray(20)))
        }
        assertEquals(HttpStatusCode.OK, requestBOk.status)

        val requestBLarge = client.post("b") {
            setBody(ByteReadChannel(ByteArray(21)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, requestBLarge.status)

        val requestCOk = client.post("c") {
            setBody(ByteReadChannel(ByteArray(1000)))
        }
        assertEquals(HttpStatusCode.OK, requestCOk.status)
    }

    @Test
    fun testBodyLimitWithContentLength() = testApplication {
        install(RequestBodyLimit) {
            bodyLimit { 15 }
        }

        routing {
            post("a") {
                call.respond("OK: ${call.parameters["path"]}")
            }
        }

        val requestOk = client.post("a") {
            setBody(ByteArray(15))
        }
        assertEquals(HttpStatusCode.OK, requestOk.status)

        val requestLarge = client.post("a") {
            setBody(ByteArray(16))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, requestLarge.status)
    }

    @Test
    fun testBodyLimitSubRouteInstall() = testApplication {
        routing {
            route("a") {
                install(RequestBodyLimit) {
                    bodyLimit { 15 }
                }
                post {
                    call.receive<ByteArray>()
                    call.respond("OK")
                }
            }
            route("b") {
                install(RequestBodyLimit) {
                    bodyLimit { 20 }
                }
                post {
                    call.receive<ByteArray>()
                    call.respond("OK")
                }
            }
            route("c") {
                post {
                    call.receive<ByteArray>()
                    call.respond("OK")
                }
            }
        }

        val requestAOk = client.post("a") {
            setBody(ByteReadChannel(ByteArray(15)))
        }
        assertEquals(HttpStatusCode.OK, requestAOk.status)

        val requestALarge = client.post("a") {
            setBody(ByteReadChannel(ByteArray(16)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, requestALarge.status)

        val requestBOk = client.post("b") {
            setBody(ByteReadChannel(ByteArray(20)))
        }
        assertEquals(HttpStatusCode.OK, requestBOk.status)

        val requestBLarge = client.post("b") {
            setBody(ByteReadChannel(ByteArray(21)))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, requestBLarge.status)

        val requestCOk = client.post("c") {
            setBody(ByteReadChannel(ByteArray(1000)))
        }
        assertEquals(HttpStatusCode.OK, requestCOk.status)
    }

    @Test
    fun channelApplyLimitTooLarge() = runTest {
        assertFailsWith<PayloadTooLargeException> {
            ByteReadChannel("This is too long")
                .applyLimit(5)
                .readUTF8Line()
        }
    }

    @Test
    fun channelApplyLimitNormal() = runTest {
        val expected = "This is OK"
        val actual = ByteReadChannel(expected)
            .applyLimit(10)
            .readUTF8Line()
        assertEquals(expected, actual)
    }

    // See KTOR-7254, happens sometimes with many threads
    @Test
    fun channelApplyLimitEmptyOnRead() = runTest {
        val channelThatClosesAfterFirstCheck = object : ByteReadChannel by ByteReadChannel.Empty {
            var calls = 0
            override val isClosedForRead get() = calls++ >= 1
        }
        val actual = channelThatClosesAfterFirstCheck
            .applyLimit(10)
            .readUTF8Line()
        assertNull(actual)
    }
}
