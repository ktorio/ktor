/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.test.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TestConnectorInterfaceUsed {
    @Test
    fun testConnectorListenHost() {
        val server = embeddedServer(CIO, applicationEnvironment(), {
            connector {
                host = "some/illegal/host/name"
                port = 9091
            }
        })

        assertFails {
            server.start()

            // this shouldn't happen
            try {
                server.stop(50, 2000)
            } catch (_: Throwable) {
            }
        }
    }

    @Test
    fun `failed start does not leak an uncaught exception`() = runTest {
        val uncaughtException = CompletableDeferred<Throwable>()
        val parentJob = SupervisorJob()
        val exceptionHandler = CoroutineExceptionHandler { _, cause -> uncaughtException.complete(cause) }

        val server = embeddedServer(
            CIO,
            serverConfig {
                parentCoroutineContext = parentJob + exceptionHandler
            },
            configure = {
                connector {
                    host = "some/illegal/host/name"
                    port = 9091
                }
            }
        )

        assertFails { server.startSuspend() }
        server.stopSuspend(50, 100)

        // On Native, an exception that reaches no handler aborts the whole test process
        withTimeout(5.seconds) {
            parentJob.children.toList().joinAll()
        }

        assertFalse(uncaughtException.isCompleted, "Server start failure leaked as an uncaught exception")
    }

    @Test
    fun `failed start throws the connector failure`() = runTest {
        val server = embeddedServer(CIO, applicationEnvironment(), {
            connector {
                host = "some/illegal/host/name"
                port = 9091
            }
        })

        val failure = assertFails { server.startSuspend() }
        server.stopSuspend(50, 100)

        assertFalse(
            failure is CancellationException,
            "Expected the connector failure, but start() threw $failure caused by ${failure.cause}"
        )
    }
}
