/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.test.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NonceSmokeTest {
    @Test
    fun test() = runTest {
        val nonceSet = HashSet<String>(4096)

        // start nonce background generation here to reduce noice in profiler
        ensureNonceGeneratorRunning()

        repeat(4098) {
            nonceSet.add(generateNonceBlocking())
        }

        assertTrue { nonceSet.size == 4098 }
    }

    @Test
    fun `test generateNonceBlocking completes when Default Dispatcher is saturated`() {
        saturateDefaultDispatcher {
            generateNonceBlocking()
        }
    }

    @Test
    fun `test generateNonceSuspend completes when Default Dispatcher is saturated`() {
        saturateDefaultDispatcher {
            generateNonceSuspend()
        }
    }

    private fun saturateDefaultDispatcher(block: suspend () -> Unit) {
        val processors = Runtime.getRuntime().availableProcessors()
        val startLatch = CountDownLatch(processors)
        val releaseLatch = CountDownLatch(1)
        val jobs = List(processors) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Default) {
                startLatch.countDown()
                releaseLatch.await(10, TimeUnit.SECONDS)
            }
        }
        startLatch.await()
        try {
            runBlocking {
                withTimeout(5.seconds) {
                    block()
                }
            }
        } finally {
            releaseLatch.countDown()
            jobs.forEach { it.cancel() }
        }
    }
}
