/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import kotlinx.coroutines.*
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.core5.http2.HttpVersionPolicy
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class NettyH2cFileResponseTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = false
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableH2c = true
    }

    @Test
    fun `serves a moderately sized file for many h2c requests`() = runTest {
        val expected = Random(42).nextBytes(FILE_SIZE)
        val file = File.createTempFile("netty-h2c-file-response", ".bin").apply {
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

            val url = "http://127.0.0.1:$port/file"
            createH2cClient().use { client ->
                repeat(REQUEST_COUNT / BATCH_SIZE) {
                    coroutineScope {
                        List(BATCH_SIZE) {
                            async(Dispatchers.IO) {
                                client.prepareGet(url).execute { response ->
                                    assertEquals(HttpStatusCode.OK, response.status)
                                    assertEquals(HttpProtocolVersion.HTTP_2_0, response.version)
                                    assertContentEquals(expected, response.bodyAsBytes())
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun createH2cClient() = HttpClient(Apache5) {
        expectSuccess = false
        engine {
            customizeClient {
                disableAutomaticRetries()
                setConnectionManager(
                    PoolingAsyncClientConnectionManagerBuilder.create()
                        .setDefaultTlsConfig(
                            TlsConfig.custom()
                                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                                .build()
                        )
                        .build()
                )
            }
        }
    }

    private companion object {
        const val FILE_SIZE = 256 * 1024
        const val REQUEST_COUNT = 300
        const val BATCH_SIZE = 20
    }
}
