/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CIOUnixSocketServerTest {

    @Test
    fun testUnixSocketEcho() = runBlocking {
        if (!UnixSocketAddress.isSupported()) return@runBlocking

        val socketFile = File.createTempFile("ktor-unix-socket-test", ".sock")
        if (socketFile.exists()) {
            socketFile.delete()
        }
        val socketPath = socketFile.absolutePath

        val server = embeddedServer(
            CIO,
            serverConfig {
                module {
                    routing {
                        get("/") {
                            call.respondText("Hello, Unix socket world!")
                        }
                        post("/echo") {
                            val text = call.receiveText()
                            call.respondText(text)
                        }
                    }
                }
            },
            configure = {
                unixConnector(socketPath)
            }
        )

        server.start(wait = false)

        try {
            delay(1000) // Wait for the server to start

            // Create a new selector for this test
            ActorSelectorManager(Dispatchers.IO).use { selector ->
                aSocket(selector).tcp().connect(UnixSocketAddress(socketPath)).use { socket ->
                    val input = socket.openReadChannel()
                    val output = socket.openWriteChannel()

                    // Simulate HTTP request
                    output.writeStringUtf8("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    output.flush()

                    // Read response
                    val statusLine = input.readLineStrict()
                    assertEquals(true, statusLine?.contains("200 OK"), "Expected HTTP 200 OK, got: $statusLine")

                    // Skip headers
                    while (true) {
                        val line = input.readLineStrict() ?: break
                        if (line.isEmpty()) break
                    }

                    // Read body
                    val responseBody = input.readLine()
                    assertEquals("Hello, Unix socket world!", responseBody)
                }
            }
        } finally {
            server.stop(1000, 1000)
            if (socketFile.exists()) {
                socketFile.delete()
            }
        }
    }
}
