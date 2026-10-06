/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class DigestProviderJvmTest {

    @Test
    fun `constructor does not block when Dispatchers Default is saturated`() {
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
            DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        } finally {
            releaseLatch.countDown()
            jobs.forEach { it.cancel() }
        }
    }
}
