/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.*
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class Apache5UnreadBodyTest {

    @Test
    fun `streaming response completes when the body arrives after the block returns`() = runTest(timeout = 10.seconds) {
        val bodySize = 2 * 1024 * 1024
        ServerSocket(0).use { server ->
            val serverThread = thread {
                try {
                    server.accept().use { socket ->
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: $bodySize\r\n\r\n".toByteArray())
                        output.flush()
                        Thread.sleep(500)
                        output.write(ByteArray(bodySize))
                        output.flush()
                    }
                } catch (_: IOException) {
                }
            }

            HttpClient(Apache5).use { client ->
                val status = client.prepareGet("http://localhost:${server.localPort}/").execute { it.status }
                assertEquals(HttpStatusCode.OK, status)
            }
            serverThread.join()
        }
    }
}
