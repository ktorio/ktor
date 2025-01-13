/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

private const val HTTP_PROXY_PORT = 8082

class AndroidProxyTest {
    private val factory: HttpClientEngineFactory<*> = Android

    @Test
    fun testProxyPost() = testWithEngine(factory) {
        config {
            engine {
                if (this is AndroidEngineConfig) {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", HTTP_PROXY_PORT))
                }
            }
        }
        test { client ->
            assertEquals("proxy", client.get("http://google.com/").body())
        }
    }
}
