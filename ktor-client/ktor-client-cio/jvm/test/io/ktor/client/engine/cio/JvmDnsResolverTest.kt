/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.random.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JvmDnsResolverTest {

    @Test
    fun testJvmDnsResolverResolvesLocalhost() = runTestWithRealTime {
        val ips = JvmDnsResolver().invoke("localhost")
        assertTrue(ips.isNotEmpty(), "localhost must resolve to at least one address")
        assertTrue(
            ips.any { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == "::1" },
            "expected loopback in $ips",
        )
    }

    @Test
    fun testJvmDnsResolverIsCooperativelyCancellable() = runTestWithRealTime {
        // Smoke test only. A strong test for `runInterruptible` interrupting an in-flight blocking
        // `InetAddress.getAllByName` call would need a hostname whose DNS lookup blocks for tens of
        // seconds, which is not portable across CI environments. This test merely verifies that the
        // resolver returns control on cancellation within a generous bound.
        val resolver = JvmDnsResolver()
        val job = launch {
            while (isActive) {
                try {
                    resolver.invoke("nonexistent-${Random.nextInt()}.invalid")
                } catch (_: UnknownHostException) {
                    // expected — `.invalid` TLD is reserved as guaranteed-non-resolving (RFC 6761)
                }
            }
        }
        delay(50.milliseconds)
        val elapsed = measureTime { job.cancelAndJoin() }
        assertTrue(elapsed < 5.seconds, "cancellation took $elapsed; expected runInterruptible to bound it")
    }
}
