/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * JVM-only fast-path tests that depend on `staticResources` (a JVM-only API). These
 * complement [RoutingFastPathTest] in the common source set.
 */
class RoutingFastPathJvmTest {

    @Test
    fun constantRoutesResolveAlongsideStaticResources() = testApplication {
        // Mirrors the user's hello-world benchmark layout exactly. The catch-all installed by
        // `staticResources("")` must not disable the routing fast path for the constant
        // `/hello` and `/clear` endpoints; otherwise every request would needlessly fall
        // through the slow DFS resolver.
        routing {
            staticResources("", basePackage = "io/ktor/server/http/content")

            get("/hello") { call.respondText("Hello, World!") }
            get("/clear") { call.respond(HttpStatusCode.OK) }
        }

        // The constant siblings must still resolve, even though the static-content tailcard
        // wrapper is registered at the same routing-root level.
        assertEquals("Hello, World!", client.get("/hello").bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/clear").status)
    }
}
