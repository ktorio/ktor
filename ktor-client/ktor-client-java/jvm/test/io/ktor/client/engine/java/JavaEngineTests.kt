/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
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
                    val response = client.get<String>("http://www.google.com")
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
                val response = client.get<String>("http://www.google.com")
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
                    val response = client.get<String>("http://www.google.com")
                    assertNotNull(response)
                }
            }
        }
    }
}
