/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.time.*
import java.util.concurrent.*
import kotlin.test.*

class JavaEngineTests {
    @Test
    fun testClose() {
        val engine = JavaHttpEngine(JavaHttpConfig())
        engine.close()

        assertTrue("Java HTTP dispatcher is not working.") {
            engine.executor.isShutdown
        }
    }

    @Test
    fun testThreadLeak() = runBlocking {
        System.setProperty("jdk.internal.httpclient.selectorTimeout", "50")

        val initialNumberOfThreads = Thread.getAllStackTraces().size
        val repeats = 25
        val executors = ArrayList<ExecutorService>()

        try {
            repeat(repeats) {
                HttpClient(Java).use { client ->
                    val response = client.get("http://www.google.com").body<String>()
                    assertNotNull(response)
                    executors += (client.engine as JavaHttpEngine).executor
                }
            }

            // When engine is disposed HttpClient's SelectorManager thread remains active
            // until it realizes that no more reference on HttpClient.
            // Minimum polling interval SelectorManager thread is 1000ms.
            var retry = 0
            do {
                System.gc()
                Thread.sleep(1000)
                System.gc()
                System.gc()
            } while (Thread.getAllStackTraces().size >= initialNumberOfThreads && retry++ < 10)
        } finally {
            System.clearProperty("jdk.internal.httpclient.selectorTimeout")
        }

        val totalNumberOfThreads = Thread.getAllStackTraces().size
        val threadsCreated = totalNumberOfThreads - initialNumberOfThreads

        executors.forEach { pool ->
            assertTrue(pool.isTerminated)
        }

        assertTrue("Number of threads should be less $repeats, but was $threadsCreated") {
            threadsCreated < repeats
        }
    }

    @Test
    fun testRequestAfterRecreate() {
        runBlocking {
            HttpClient(Java)
                .close()

            HttpClient(Java).use { client ->
                val response = client.get("http://www.google.com").body<String>()
                assertNotNull(response)
            }
        }
    }

    @Test
    fun testSubsequentRequests() {
        runBlocking {
            HttpClient(Java)
                .close()

            HttpClient(Java).use { client ->
                repeat(3) {
                    val response = client.get("http://www.google.com").body<String>()
                    assertNotNull(response)
                }
            }
        }
    }

    @Test
    fun testProtocolVersion() = runBlocking {
        HttpClient(Java) {
            engine {
                protocolVersion = java.net.http.HttpClient.Version.HTTP_2
            }
        }.use { client ->
            val response = client.get("https://httpbin.org/get")
            assertEquals(HttpProtocolVersion.HTTP_2_0, response.version)
        }
    }

    @Test
    fun infiniteConnectTimeout() = runBlocking {
        HttpClient(Java) {
            install(HttpTimeout) {
                connectTimeoutMillis = Long.MAX_VALUE
            }
        }.use { client ->
            val response = withTimeout(1000) { client.get("http://www.google.com") }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun infiniteRequestTimeout() = runBlocking {
        HttpClient(Java) {
            install(HttpTimeout)
        }.use { client ->
            val response = withTimeout(1000) {
                client.get("http://www.google.com") {
                    timeout { requestTimeoutMillis = Long.MAX_VALUE }
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun usualConnectTimeout() = runBlocking {
        HttpClient(Java) {
            install(HttpTimeout) {
                connectTimeoutMillis = 1000
            }
        }.use { client ->
            val response = withTimeout(1000) { client.get("http://www.google.com") }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun usualRequestTimeout() = runBlocking {
        HttpClient(Java) {
            install(HttpTimeout)
        }.use { client ->
            val response = withTimeout(1000) {
                client.get("http://www.google.com") {
                    timeout { requestTimeoutMillis = 3000 }
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun isTimeoutInfiniteFunction() {
        assertTrue(isTimeoutInfinite(HttpTimeout.INFINITE_TIMEOUT_MS))
        assertTrue(isTimeoutInfinite(Long.MAX_VALUE, Instant.ofEpochMilli(1)))

        assertFalse(isTimeoutInfinite(1000, Instant.ofEpochMilli(0)))
        assertFalse(isTimeoutInfinite(0, Instant.ofEpochMilli(0)))
        assertFalse(isTimeoutInfinite(0, Instant.ofEpochMilli(Long.MAX_VALUE)))
    }
}
