/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.cio.internal.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.*
import io.ktor.util.PlatformUtils
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.files.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.*

class CIOUnixSocketServerTest {
    @OptIn(ExperimentalUuidApi::class)
    private fun createTempFilePath(basename: String): String {
        return Path(SystemTemporaryDirectory, "$basename-${Uuid.random()}").toString()
    }

    private fun removeFile(path: String) {
        SystemFileSystem.delete(Path(path), mustExist = false)
    }

    @Test
    fun testUnixSocketEcho() = runTest {
        if (!UnixSocketAddress.isSupported()) return@runTest
        // skipped because kotlinx-io is not compatible with ES modules on JS
        // https://github.com/Kotlin/kotlinx-io/issues/345
        if (PlatformUtils.IS_JS) return@runTest

        val socketPath = createTempFilePath("ktor-unix-test")

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

        server.startSuspend(wait = false)

        try {
            delay(1000.milliseconds) // Wait for the server to start

            // Create a new selector for this test
            SelectorManager(Dispatchers.IOBridge).use { selector ->
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
            server.stopSuspend(1000, 1000)
            removeFile(socketPath)
        }
    }
}
