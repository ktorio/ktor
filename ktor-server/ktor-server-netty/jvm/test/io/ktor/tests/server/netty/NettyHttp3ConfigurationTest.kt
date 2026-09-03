/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3ConfigurationTest {

    private fun assertRejects(expectedMessage: String, configure: NettyApplicationEngine.Configuration.() -> Unit) {
        val failure = assertFailsWith<IllegalArgumentException> {
            NettyApplicationEngine.Configuration().apply(configure)
        }
        assertEquals(expectedMessage, failure.message)
    }

    @Test
    fun `quicMaxIdleTimeout must be positive`() {
        assertRejects("quicMaxIdleTimeout must be > 0, but was 0s") {
            enableHttp3 { quicMaxIdleTimeout = Duration.ZERO }
        }
        assertRejects("quicMaxIdleTimeout must be > 0, but was -1s") {
            enableHttp3 { quicMaxIdleTimeout = (-1).seconds }
        }
    }

    @Test
    fun `quicInitialMaxData must be positive`() {
        assertRejects("quicInitialMaxData must be > 0, but was 0") {
            enableHttp3 { quicInitialMaxData = 0 }
        }
        assertRejects("quicInitialMaxData must be > 0, but was -1") {
            enableHttp3 { quicInitialMaxData = -1 }
        }
    }

    @Test
    fun `quicInitialMaxStreamDataBidirectionalLocal must be positive`() {
        assertRejects("quicInitialMaxStreamDataBidirectionalLocal must be > 0, but was 0") {
            enableHttp3 { quicInitialMaxStreamDataBidirectionalLocal = 0 }
        }
    }

    @Test
    fun `quicInitialMaxStreamDataBidirectionalRemote must be positive`() {
        assertRejects("quicInitialMaxStreamDataBidirectionalRemote must be > 0, but was 0") {
            enableHttp3 { quicInitialMaxStreamDataBidirectionalRemote = 0 }
        }
    }

    @Test
    fun `quicInitialMaxStreamsBidirectional must be positive`() {
        assertRejects("quicInitialMaxStreamsBidirectional must be > 0, but was 0") {
            enableHttp3 { quicInitialMaxStreamsBidirectional = 0 }
        }
    }

    @Test
    fun `udpSocketCount must be positive when set`() {
        assertRejects("udpSocketCount must be > 0, but was 0") {
            enableHttp3 { udpSocketCount = 0 }
        }
        assertRejects("udpSocketCount must be > 0, but was -1") {
            enableHttp3 { udpSocketCount = -1 }
        }
    }

    @Test
    fun `udp buffer sizes must not be negative`() {
        assertRejects("udpReceiveBufferSize must be >= 0, but was -1") {
            enableHttp3 { udpReceiveBufferSize = -1 }
        }
        assertRejects("udpSendBufferSize must be >= 0, but was -1") {
            enableHttp3 { udpSendBufferSize = -1 }
        }
    }

    @Test
    fun `defaults match the documented option table`() {
        val configuration = NettyApplicationEngine.Configuration().apply { enableHttp3() }
        val http3 = assertNotNull(configuration.http3Configuration)

        assertNull(http3.quicTokenHandler, "no Retry by default")
        assertEquals(30.seconds, http3.quicMaxIdleTimeout)
        assertEquals(10_000_000, http3.quicInitialMaxData)
        assertEquals(1_000_000, http3.quicInitialMaxStreamDataBidirectionalLocal)
        assertEquals(1_000_000, http3.quicInitialMaxStreamDataBidirectionalRemote)
        assertEquals(100, http3.quicInitialMaxStreamsBidirectional)
        assertNull(http3.udpSocketCount, "null means the engine decides, not one socket")
        assertEquals(0, http3.udpReceiveBufferSize, "zero leaves the OS default")
        assertEquals(0, http3.udpSendBufferSize, "zero leaves the OS default")
    }

    @Test
    fun `zero is accepted for the udp buffer sizes and leaves the OS default`() {
        val configuration = NettyApplicationEngine.Configuration().apply {
            enableHttp3 {
                udpReceiveBufferSize = 0
                udpSendBufferSize = 0
            }
        }

        val http3 = assertNotNull(configuration.http3Configuration)
        assertEquals(0, http3.udpReceiveBufferSize)
        assertEquals(0, http3.udpSendBufferSize)
    }

    @Test
    fun `udpSocketCount accepts null`() {
        val configuration = NettyApplicationEngine.Configuration().apply {
            enableHttp3 { udpSocketCount = null }
        }

        assertNull(assertNotNull(configuration.http3Configuration).udpSocketCount)
    }

    @Test
    fun `calling enableHttp3 twice replaces the previous configuration`() {
        val configuration = NettyApplicationEngine.Configuration().apply {
            enableHttp3 { quicInitialMaxData = 1_000 }
            enableHttp3 { quicInitialMaxData = 2_000 }
        }

        assertEquals(2_000, assertNotNull(configuration.http3Configuration).quicInitialMaxData)
    }
}
