/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingFastPathJvmTest {

    @Test
    fun `constant routes resolved along static files`() = testApplication {
        routing {
            staticResources("", basePackage = "io/ktor/server/http/content")

            get("/hello") { call.respondText("Hello, World!") }
            get("/clear") { call.respond(HttpStatusCode.OK) }
        }

        assertEquals("Hello, World!", client.get("/hello").bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/clear").status)
    }
}
