/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class NettyHttp2FileResponseTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableHttp2 = true
    }

    @Test
    fun `serves a moderately sized file for many HTTP2 requests`() = runTest {
        val expected = Random(42).nextBytes(FILE_SIZE)
        val file = File.createTempFile("netty-h2-file-response", ".bin").apply {
            deleteOnExit()
            writeBytes(expected)
        }

        try {
            createAndStartServer {
                application.routing {
                    get("/file") {
                        call.respondFile(file)
                    }
                }
            }

            val url = "https://127.0.0.1:$sslPort/file"
            repeat(REQUEST_COUNT / BATCH_SIZE) {
                coroutineScope {
                    List(BATCH_SIZE) {
                        async(Dispatchers.IO) {
                            withHttp2(url, sslPort, {}) {
                                assertEquals(HttpStatusCode.OK, status)
                                assertEquals(HttpProtocolVersion.HTTP_2_0, version)
                                assertContentEquals(expected, bodyAsBytes())
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val FILE_SIZE = 256 * 1024
        const val REQUEST_COUNT = 300
        const val BATCH_SIZE = 20
    }
}
