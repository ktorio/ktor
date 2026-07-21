/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NonceSmokeTest {
    @Test
    fun test() {
        val nonceSet = HashSet<String>(4096)

        repeat(4096) {
            nonceSet.add(generateNonceBlocking())
        }

        assertTrue { nonceSet.size == 4096 }
    }

    @Test
    fun `test generate nonce blocking does not deadlock when Default Dispatcher is saturated`() {
        val processors = Runtime.getRuntime().availableProcessors()
        val latch = CountDownLatch(processors)
        val jobs = List(processors) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Default) {
                latch.countDown()
                Thread.sleep(10_000)
            }
        }
        latch.await()
        try {
            runBlocking {
                withTimeout(5.seconds) {
                    generateNonceBlocking()
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
        }
    }
}
