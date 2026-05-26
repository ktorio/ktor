/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class HttpTimeoutJvmTest {

    @Test
    fun `instant response does not trigger timeout in virtual time`() = runTest(timeout = 30.seconds) {
        val server = embeddedServer(Netty, port = 0) {
            routing {
                post("/x") { call.respond(HttpStatusCode.OK) }
            }
        }.apply { start() }

        try {
            val port = server.engine.resolvedConnectors().first().port

            val client = HttpClient(CIO) {
                install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            }
            try {
                val response = client.post("http://localhost:$port/x")
                assertEquals(HttpStatusCode.OK, response.status)
            } finally {
                client.close()
            }
        } finally {
            server.stop(0, 0)
        }
    }
}
