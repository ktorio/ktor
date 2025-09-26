/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.bodyAsText
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UnixSocketTest {

    @Test
    fun testUnixSocketClient() = runBlocking {
        if (!UnixSocketAddress.isSupported()) return@runBlocking

        val server = embeddedServer(
            CIO,
            serverConfig {
                module {
                    routing {
                        get("/") {
                            call.respondText("Hello, Unix socket world!")
                        }
                    }
                }
            },
            configure = {
                unixConnector("/tmp/test-unix-socket-client.sock")
            }
        )

        val client = HttpClient(io.ktor.client.engine.cio.CIO)
        try {
            server.start(wait = false)
            delay(1000)

            val response = client.get("http://localhost/") {
                unixSocket("/tmp/test-unix-socket-client.sock/")
            }
            assertEquals(200, response.status.value)
            assertEquals("Hello, Unix socket world!", response.bodyAsText())
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }

    @Test
    fun testUnixSocketClientWithDefaultRequest() = runBlocking {
        if (!UnixSocketAddress.isSupported()) return@runBlocking

        val server = embeddedServer(
            CIO,
            serverConfig {
                module {
                    routing {
                        get("/test") {
                            call.respondText("Hello from default Unix socket!")
                        }
                        get("/custom") {
                            call.respondText("Hello from custom endpoint!")
                        }
                    }
                }
            },
            configure = {
                unixConnector("/tmp/test-unix-socket-default.sock")
            }
        )

        val client = HttpClient(io.ktor.client.engine.cio.CIO) {
            defaultRequest {
                unixSocket("/tmp/test-unix-socket-default.sock/")
            }
        }

        try {
            server.start(wait = false)
            delay(1000)

            // Test that the default Unix socket is used
            val response1 = client.get("http://localhost/test")
            assertEquals(200, response1.status.value)
            assertEquals("Hello from default Unix socket!", response1.bodyAsText())

            // Test another endpoint with the same default Unix socket
            val response2 = client.get("http://localhost/custom")
            assertEquals(200, response2.status.value)
            assertEquals("Hello from custom endpoint!", response2.bodyAsText())
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }
}
