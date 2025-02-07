/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private const val HTTP_PROXY_SERVER: String = TCP_SERVER

/**
 * This is a temporary tests that should be moved to the general test suite
 * once we support TLS options in client configs to connect to the local test TLS server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.winhttp.WinHttpProxyTest)
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
}
