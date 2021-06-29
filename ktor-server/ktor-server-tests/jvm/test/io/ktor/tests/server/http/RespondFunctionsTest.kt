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
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("0, 1, 2, 3, 4, 5, 6, 7, 8, 9", call.response.byteContent?.joinToString())
            assertFalse(call.response.headers.contains(HttpHeaders.ContentType))
        }
        handleRequest(HttpMethod.Get, "/provider").let { call ->
            assertEquals("0, 1, 2, 3, 4, 5, 6, 7, 8, 9", call.response.byteContent?.joinToString())
            assertFalse(call.response.headers.contains(HttpHeaders.ContentType))
        }
    }
}
