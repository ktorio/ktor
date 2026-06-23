/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.*
import io.ktor.util.PlatformUtils
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class UnixSocketTest {

    @Test
    fun testUnixSocketClient() = runTest {
        if (!UnixSocketAddress.isSupported()) return@runTest
        // skipped because kotlinx-io is not compatible with ES modules on JS
        // https://github.com/Kotlin/kotlinx-io/issues/345
        if (PlatformUtils.IS_JS) return@runTest

        val server = embeddedServer(
            ServerCIO,
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

        val client = HttpClient(ClientCIO)
        try {
            server.startSuspend(wait = false)
            delay(1000.milliseconds)

            val response = client.get("http://localhost/") {
                unixSocket("/tmp/test-unix-socket-client.sock")
            }
            assertEquals(200, response.status.value)
            assertEquals("Hello, Unix socket world!", response.bodyAsText())
        } finally {
            client.close()
            server.stopSuspend(0, 0)
        }
    }

    @Test
    fun testUnixSocketClientWithDefaultRequest() = runTest {
        if (!UnixSocketAddress.isSupported()) return@runTest
        // skipped because kotlinx-io is not compatible with ES modules on JS
        // https://github.com/Kotlin/kotlinx-io/issues/345
        if (PlatformUtils.IS_JS) return@runTest

        val server = embeddedServer(
            ServerCIO,
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

        val client = HttpClient(ClientCIO) {
            defaultRequest {
                unixSocket("/tmp/test-unix-socket-default.sock")
            }
        }

        try {
            server.startSuspend(wait = false)
            delay(1000.milliseconds)

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
            server.stopSuspend(0, 0)
        }
    }
}
