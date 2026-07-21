/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import kotlin.test.Test

class DigestProviderJvmTest {

    @Test
    fun `constructor does not block when Dispatchers Default is saturated`() {
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
            DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        } finally {
            jobs.forEach { it.cancel() }
        }
    }
}
