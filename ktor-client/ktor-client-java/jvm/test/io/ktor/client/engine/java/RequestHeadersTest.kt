/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that headers the JDK `HttpClient` no longer restricts are delegated to it and reach the
 * server, rather than being silently dropped by the engine's `DISALLOWED_HEADERS` filter.
 */
class RequestHeadersTest : TestWithKtor() {

    override val server = embeddedServer(CIO, serverPort) {
        routing {
            get("/echo-header") {
                val name = call.parameters.getOrFail("name")
                call.respondText(call.request.header(name) ?: ABSENT)
            }
        }
    }

    @Test
    fun `caller-supplied Date header reaches the server`() =
        assertHeaderReachesServer(HttpHeaders.Date, "Wed, 21 Oct 2015 07:28:00 GMT")

    @Test
    fun `caller-supplied From header reaches the server`() =
        assertHeaderReachesServer(HttpHeaders.From, "user@example.com")

    @Test
    fun `caller-supplied Via header reaches the server`() =
        assertHeaderReachesServer(HttpHeaders.Via, "1.1 ktor")

    @Test
    fun `caller-supplied Warning header reaches the server`() =
        assertHeaderReachesServer(HttpHeaders.Warning, "199 ktor test")

    private fun assertHeaderReachesServer(name: String, value: String) = runBlocking {
        HttpClient(Java).use { client ->
            val received = client.get("$testUrl/echo-header?name=$name") {
                header(name, value)
            }.body<String>()
            assertEquals(value, received, "$name header should reach the server unchanged")
        }
    }

    private companion object {
        private const val ABSENT = "<absent>"
    }
}
