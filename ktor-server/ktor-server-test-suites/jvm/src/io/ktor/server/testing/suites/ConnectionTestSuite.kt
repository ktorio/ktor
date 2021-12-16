/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.server.engine.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

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
        @OptIn(ExperimentalTime::class)
        val addresses = withTimeout(15.seconds) {
            server.resolvedConnectors()
        }

        assertEquals(2, addresses.size)
        assertFalse(addresses.any { it.port == 0 })
        server.stop(1.seconds, 1.seconds)
    }
}
