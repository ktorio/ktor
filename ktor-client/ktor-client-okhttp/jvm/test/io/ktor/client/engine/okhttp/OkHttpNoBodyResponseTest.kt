/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class OkHttpNoBodyResponseTest {

    @Test
    fun `304 response with Content-Length and no body does not wait for the body`() = runTest(timeout = 10.seconds) {
        ServerSocket(0).use { server ->
            val serverThread = thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 304 Not Modified\r\nContent-Length: 42\r\n\r\n".toByteArray())
                            flush()
                        }
                        socket.getInputStream().readBytes()
                    }
                } catch (_: SocketException) {
                }
            }

            HttpClient(OkHttp).use { client ->
                val bodySize = client.prepareGet("http://localhost:${server.localPort}/").execute { response ->
                    response.bodyAsChannel().readRemaining().readByteArray().size
                }
                assertEquals(0, bodySize)
            }
            serverThread.join()
        }
    }
}
