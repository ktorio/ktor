/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.bodyAsText
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.request.*
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
            server.startSuspend(wait = false)
            delay(1000)

            val response = client.get("http://localhost/") {
                unixSocket("/tmp/test-unix-socket-client.sock/")
            }
            assertEquals(200, response.status.value)
            assertEquals("Hello, Unix socket world!", response.bodyAsText())
        } finally {
            client.close()
            server.stop()
        }
    }
}
