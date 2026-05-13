/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class CookieHeaderCaseTest {

    @Test
    fun `CIO preserves Set-Cookie headers regardless of case`() = runTest {
        HttpClient(CIO).use { client ->
            ServerSocket(0).use { server ->
                val thread = thread {
                    try {
                        server.accept().use { socket ->
                            socket.getOutputStream().let { out ->
                                out.write(
                                    (
                                        "HTTP/1.1 200 OK\r\n" +
                                            "Content-Length: 0\r\n" +
                                            "Set-Cookie: first=1\r\n" +
                                            "set-cookie: second=2\r\n" +
                                            "\r\n"
                                        ).toByteArray()
                                )
                                out.flush()
                            }
                        }
                    } catch (_: SocketException) {
                    }
                }

                val response = client.get("http://localhost:${server.localPort}/")
                assertEquals(
                    listOf("first=1", "second=2"),
                    response.headers.getAll(HttpHeaders.SetCookie)
                )
                thread.join()
            }
        }
    }
}
