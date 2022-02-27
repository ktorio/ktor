/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class DefaultHeadersTest {
    @Test
    fun testDate(): Unit = withTestApplication {
        var now = 1569882841014
        application.install(DefaultHeaders) {
            clock = { now }
        }

        application.intercept(ApplicationCallPipeline.Call) {
            call.respondText("OK")
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", call.response.headers[HttpHeaders.Date])
        }

        now += 999

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", call.response.headers[HttpHeaders.Date])
        }

        now++

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("Mon, 30 Sep 2019 22:34:02 GMT", call.response.headers[HttpHeaders.Date])
        }
    }

    @Test
    fun testCustomHeader(): Unit = withTestApplication {
        application.install(DefaultHeaders) {
            header("X-Test", "123")
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("123", call.response.headers["X-Test"])
        }
    }

    @Test
    fun testDefaultServerHeader(): Unit = withTestApplication {
        application.install(DefaultHeaders) {
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            val serverHeader = call.response.headers[HttpHeaders.Server]
            assertNotNull(serverHeader)
            assertTrue("Server header invalid: $serverHeader") { "Ktor" in serverHeader }
        }
    }

    @Test
    fun testCustomServerHeader(): Unit = withTestApplication {
        application.install(DefaultHeaders) {
            header(HttpHeaders.Server, "MyServer")
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("MyServer", call.response.headers[HttpHeaders.Server])
        }
    }
}
