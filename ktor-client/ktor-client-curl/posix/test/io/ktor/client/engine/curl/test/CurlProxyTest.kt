/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * This is a temporary tests that should be moved to the general test suite
 * once we support TLS options in client configs to connect to the local test TLS server.
 */
class CurlProxyTest {
    @Test
    fun plainHttpTest() {
        val client = HttpClient(Curl) {
            engine {
                proxy = ProxyBuilder.http(URLBuilder().apply {
                    host = "localhost"
                    port = 8082
                }.build())
            }
        }

        client.use {
            runBlocking {
                assertEquals("Hello", client.get<String>("$@@/content/hello"))
            }
        }
    }

    @Test
    fun httpsOverTunnelTest() {
        val client = HttpClient(Curl) {
            engine {
                this as CurlClientEngineConfig

                forceProxyTunneling = true
                sslVerify = false

                proxy = ProxyBuilder.http(URLBuilder().apply {
                    host = "localhost"
                    port = 8082
                }.build())
            }
        }

        runBlocking {
            client.use {
                client.get<HttpResponse>("https://localhost:8089/").let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("Hello, TLS!", response.readText())
                    assertEquals("text/plain;charset=utf-8", response.headers[HttpHeaders.ContentType])
                    assertEquals("TLS test server", response.headers["X-Comment"])
                }
            }
        }
    }
}
