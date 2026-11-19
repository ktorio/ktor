/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val HTTP_PROXY_SERVER: String = TCP_SERVER

/**
 * This is a temporary tests that should be moved to the general test suite
 * once we support TLS options in client configs to connect to the local test TLS server.
 */
class WinHttpProxyTest : ClientEngineTest<WinHttpClientEngineConfig>(WinHttp) {

    @Test
    fun plainHttpTest() = testClient {
        config {
            engine {
                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        test { client ->
            runBlocking {
                assertEquals("hello", client.get("$TEST_SERVER/content/hello").body())
            }
        }
    }

    @Test
    fun proxyAuthorizationIsNotSentOverHttps() = testClient {
        config {
            engine {
                sslVerify = false
                proxy = ProxyBuilder.http(HTTP_PROXY_SERVER)
            }
        }

        test { client ->
            for (name in listOf(HttpHeaders.ProxyAuthorization, "proxy-authorization")) {
                val response = client.get("https://localhost:8089/echo/headers") {
                    header(name, "Basic secret")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val headers = response.bodyAsText()

                assertFalse("proxy-authorization" in headers, "$name was sent to the origin server:\n$headers")
            }
        }
    }
}
