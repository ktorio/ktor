/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

@Suppress("DEPRECATION")
class DefaultHeadersTest {

    @Test
    fun addsServerHeaderWithFallbackPackageNameAndVersion() = withTestApplication {
        application.install(DefaultHeaders)
        application.routing {
            get { call.respond("OK") }
        }
        handleRequest(HttpMethod.Get, "/").let { result ->
            assertTrue(result.response.headers["Server"]!!.startsWith("Ktor/"))
        }
    }

    @Test
    fun serverHeaderIsNotModifiedIfPresent() = withTestApplication {
        application.install(DefaultHeaders) {
            header(HttpHeaders.Server, "xserver/1.0")
        }
        application.routing {
            get { call.respond("OK") }
        }
        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals("xserver/1.0", result.response.headers["Server"])
        }
    }

    @Test
    fun testSubrouteInstall(): Unit = withTestApplication {
        application.routing {
            route("1") {
                install(DefaultHeaders) {}
                get { call.respond("response") }
            }
            get("2") { call.respond("response") }
        }

        handleRequest(HttpMethod.Get, "/1").let { result ->
            assertTrue(result.response.headers["Server"]!!.startsWith("Ktor/"))
        }
        handleRequest(HttpMethod.Get, "/2").let { result ->
            assertNull(result.response.headers["Server"])
        }
    }
}
