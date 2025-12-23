/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.*
import kotlin.test.*

class JettyHandlingCapacityTest : EngineTestBase<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    init {
        enableSsl = false
    }

    private val corePoolSize = 2
    private val concurrentRequests = 10

    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        configuration.callGroupSize = corePoolSize
    }

    /**
     * Verifies that the thread pool expands beyond the configured callGroupSize when handlers block,
     * handling these requests immediately.
     */
    @Test
    fun testBlockingHandlersPoolExpansion() = runTest {
        val handlerEnteredLatch = CountDownLatch(concurrentRequests)
        val blockHandlerLatch = CountDownLatch(1)
        val handlingThreads = ConcurrentHashMap.newKeySet<String>()

        createAndStartServer {
            get("/blocking") {
                handlingThreads.add(Thread.currentThread().name)
                handlerEnteredLatch.countDown()
                blockHandlerLatch.await()
                call.respondText("OK")
            }
        }

        val clientJobs = List(concurrentRequests) {
            launch {
                withUrl("/blocking") { }
            }
        }

        // If the pool does not expand beyond callGroupSize, this will timeout
        // because only callGroupSize requests can be handled concurrently
        val allEntered = runInterruptible(Dispatchers.IO) { handlerEnteredLatch.await(5, TimeUnit.SECONDS) }

        blockHandlerLatch.countDown()
        clientJobs.joinAll()

        assertTrue(
            allEntered,
            "Not all $concurrentRequests requests entered handler concurrently - pool did not expand"
        )
        assertTrue(
            handlingThreads.size > corePoolSize,
            "Expected pool expansion beyond $corePoolSize core threads, but only ${handlingThreads.size} unique threads handled requests"
        )
        assertTrue(
            handlingThreads.all { it.startsWith("ktor-jetty-") },
            "Expected all handling threads to be from JettyKtorHandler pool, but got: $handlingThreads"
        )
    }
}
