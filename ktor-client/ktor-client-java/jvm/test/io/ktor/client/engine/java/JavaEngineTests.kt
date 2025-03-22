/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Instant
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class JavaEngineTests : ClientEngineTest<JavaHttpConfig>(Java) {

    @Test
    fun testProxy() = testClient {
        config {
            engine {
                proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("localhost", 8082))
            }
        }

        test { client ->
            val body = client.get("http://127.0.0.1:8080/")
                .bodyAsText()

            assertEquals("proxy", body)
        }
    }

    @Test
    fun testRequestAfterRecreate() = runTestWithRealTime {
        HttpClient(Java)
            .close()

        HttpClient(Java).use { client ->
            val response = client.get("http://www.google.com").body<String>()
            assertNotNull(response)
        }
    }

    @Test
    fun testSubsequentRequests() = runTestWithRealTime {
        HttpClient(Java)
            .close()

        HttpClient(Java).use { client ->
            repeat(3) {
                val response = client.get("http://www.google.com").body<String>()
                assertNotNull(response)
            }
        }
    }

    @Test
    fun testProtocolVersion() = testClient {
        config {
            engine {
                protocolVersion = java.net.http.HttpClient.Version.HTTP_2
            }
        }

        test { client ->
            val response = client.get("https://httpbin.org/get")
            assertEquals(HttpProtocolVersion.HTTP_2_0, response.version)
        }
    }

    @Test
    fun infiniteConnectTimeout() = testClient(timeout = 1.seconds) {
        config {
            install(HttpTimeout) {
                connectTimeoutMillis = Long.MAX_VALUE
            }
        }

        test { client ->
            val response = client.get("http://www.google.com")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun infiniteRequestTimeout() = testClient(timeout = 1.seconds) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.get("http://www.google.com") {
                timeout { requestTimeoutMillis = Long.MAX_VALUE }
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun usualConnectTimeout() = testClient(timeout = 1.seconds) {
        config {
            install(HttpTimeout) {
                connectTimeoutMillis = 1000
            }
        }

        test { client ->
            val response = client.get("http://www.google.com")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun usualRequestTimeout() = testClient(timeout = 1.seconds) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.get("http://www.google.com") {
                timeout { requestTimeoutMillis = 3000 }
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun isTimeoutInfiniteFunction() {
        assertTrue(isTimeoutInfinite(HttpTimeoutConfig.INFINITE_TIMEOUT_MS))
        assertTrue(isTimeoutInfinite(Long.MAX_VALUE, Instant.ofEpochMilli(1)))

        assertFalse(isTimeoutInfinite(1000, Instant.ofEpochMilli(0)))
        assertFalse(isTimeoutInfinite(0, Instant.ofEpochMilli(0)))
        assertFalse(isTimeoutInfinite(0, Instant.ofEpochMilli(Long.MAX_VALUE)))
    }
}
