/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class RespondFunctionsTest {
    @Test
    fun testRespondBytes(): Unit = withTestApplication {
        application.routing {
            get("/") {
                call.respondBytes(ByteArray(10) { it.toByte() })
            }
            get("/provider") {
                call.respondBytes { ByteArray(10) { it.toByte() } }
            }
            get("/bytes-writer") {
                call.respondBytesWriter(contentLength = 2) { writeByte(1); writeByte(2) }
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("0, 1, 2, 3, 4, 5, 6, 7, 8, 9", call.response.byteContent?.joinToString())
            assertFalse(call.response.headers.contains(HttpHeaders.ContentType))
        }
        handleRequest(HttpMethod.Get, "/provider").let { call ->
            assertEquals("0, 1, 2, 3, 4, 5, 6, 7, 8, 9", call.response.byteContent?.joinToString())
            assertFalse(call.response.headers.contains(HttpHeaders.ContentType))
        }
        handleRequest(HttpMethod.Get, "/bytes-writer").let { call ->
            assertEquals("1, 2", call.response.byteContent?.joinToString())
            assertEquals("2", call.response.headers[HttpHeaders.ContentLength])
        }
    }
}
