/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

class DefaultHeadersTest {

    @ExperimentalTime
    @Test
    fun testDate(): Unit = testApplication {
        val testTimeSource = TestTimeSource()
        environment {
            clock = testTimeSource.toGMTClock(offset = GMTDate(durationSinceEpoch = 1569882841014.milliseconds))
        }
        application {
            install(DefaultHeaders)

            intercept(ApplicationCallPipeline.Call) {
                call.respondText("OK")
            }
        }

        assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", client.get("/").headers[HttpHeaders.Date])

        testTimeSource += 999.milliseconds
        assertEquals("Mon, 30 Sep 2019 22:34:01 GMT", client.get("/").headers[HttpHeaders.Date])

        testTimeSource += 1.milliseconds
        assertEquals("Mon, 30 Sep 2019 22:34:02 GMT", client.get("/").headers[HttpHeaders.Date])
    }

    @Test
    fun testCustomHeader(): Unit = testApplication {
        application {
            install(DefaultHeaders) {
                header("X-Test", "123")
            }
        }
        assertEquals("123", client.get("/").headers["X-Test"])
    }

    @Test
    fun testDefaultServerHeader(): Unit = testApplication {
        application {
            install(DefaultHeaders) {
            }
        }
        client.get("/").let { call ->
            val serverHeader = call.headers[HttpHeaders.Server]
            assertNotNull(serverHeader)
            assertTrue("Server header invalid: $serverHeader") { "Ktor" in serverHeader }
        }
    }

    @Test
    fun testCustomServerHeader(): Unit = testApplication {
        application {
            install(DefaultHeaders) {
                header(HttpHeaders.Server, "MyServer")
            }
        }

        assertEquals("MyServer", client.get("/").headers[HttpHeaders.Server])
    }
}
