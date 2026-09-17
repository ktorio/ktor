/*
* Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import kotlin.test.*

class ProxyTest {

    @Test
    fun testSocksProxyThrowsInsteadOfBeingSilentlyIgnored() {
        assertFailsWith<IllegalStateException> {
            HttpClient(CIO) {
                engine {
                    proxy = ProxyBuilder.socks("localhost", 1080)
                }
            }
        }
    }

    @Test
    fun testHttpProxyIsAccepted() {
        val client = HttpClient(CIO) {
            engine {
                proxy = ProxyBuilder.http(Url("http://localhost:8080"))
            }
        }
        client.close()
    }
}
