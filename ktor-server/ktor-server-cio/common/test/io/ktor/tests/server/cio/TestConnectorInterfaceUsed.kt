/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlin.test.*

class TestConnectorInterfaceUsed {
    @Test
    fun testConnectorListenHost() {
        val server = embeddedServer(
            CIO,
            serverConfig(applicationEnvironment()) {
                // The illegal host below makes this server fail to start by design. The
                // engine's root coroutine rethrows the startup failure, and on native
                // targets an uncaught coroutine exception terminates the whole test
                // process, randomly failing whichever test is running next. Contain the
                // expected failure here so it is only reported by server.start().
                parentCoroutineContext = CoroutineExceptionHandler { _, cause ->
                    println("Expected startup failure: $cause")
                }
            }
        ) {
            connector {
                host = "some/illegal/host/name"
                port = 9091
            }
        }

        assertFails {
            server.start()

            // this shouldn't happen
            try {
                server.stop(50, 2000)
            } catch (_: Throwable) {
            }
        }
    }
}
