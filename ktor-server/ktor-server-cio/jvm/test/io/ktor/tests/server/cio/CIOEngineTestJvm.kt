/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.network.selector.Selectable
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.httpServer
import io.ktor.server.engine.*
import io.ktor.server.testing.suites.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class CIOCompressionTest : CompressionTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOContentTest : ContentTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOHttpServerJvmTest : HttpServerJvmTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class CIOSustainabilityTest : SustainabilityTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore
    override fun validateCallCoroutineContext() {}
}

class CIOConfigTest : ConfigTestSuite(CIO)

class CIOConnectionTest : ConnectionTestSuite(CIO) {
    @Test
    fun testShutdownGracePeriodWithConnector() {
        val server = embeddedServer(
            factory = CIO,
            environment = applicationEnvironment(),
            configure = {
                shutdownGracePeriod = 10_000
                connector {
                    port = 8787
                }
            },
        ).start(wait = false)

        val time = measureTimeMillis {
            server.stop()
        }

        assertTrue { time < 5_000 }
    }

    @Test
    fun `accept loop retries on failure`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())

        try {
            val server = scope.httpServer(HttpServerSettings(host = "127.0.0.1", port = 0)) {}

            // Closing only the underlying channel keeps the server socket open from Ktor's point of view,
            // so every subsequent accept() fails synchronously and the accept loop has to retry
            (server.serverSocket.await() as Selectable).channel.close()

            val probe = CompletableDeferred<Unit>()
            scope.launch { probe.complete(Unit) }
            assertNotNull(
                withTimeoutOrNull(2.seconds) { probe.await() },
                "Accept loop starves the dispatcher: it is spinning on failed accept() calls"
            )
            assertTrue(server.acceptJob.isActive, "Accept loop should keep retrying after a failed accept()")

            server.acceptJob.cancel()
            assertNotNull(
                withTimeoutOrNull(2.seconds) { server.acceptJob.join() },
                "Accept loop did not stop after cancellation"
            )
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }
}

class CIOPluginsTest : ServerPluginsTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOHooksTest : HooksTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class CIOHttpRequestLifecycleTest :
    HttpRequestLifecycleTest<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableSsl = false
        enableHttp2 = false
    }

    @Ignore
    override fun testPipelinedRequestsCancelledOnDisconnect() {}
}
