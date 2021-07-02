/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.server.engine.*
import io.ktor.util.network.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.net.*

abstract class ConnectionTestSuite(val engine: ApplicationEngineFactory<*, *>) {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testNetworkAddresses() = runBlocking {
        val server = embeddedServer(
            engine,
            applicationEngineEnvironment {
                connector { port = 0 }
                connector { port = ServerSocket(0).use { it.localPort } }
            }
        ) {
        }

        GlobalScope.launch {
            server.start(true)
        }

        val addresses = withTimeout(15000) {
            server.networkAddresses()
        }

        assertEquals(2, addresses.size)
        assertFalse(addresses.any { it.port == 0 })
        server.stop(1000, 1000)
    }
}
