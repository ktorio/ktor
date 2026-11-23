/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.engine.cio.CIO as CioClient
import io.ktor.server.cio.CIO as CioServer

class CIOIODispatcherTest {

    @Test
    fun `calls are handled on configured ioDispatcher`() = runBlocking {
        val executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "cio-custom-io-thread").apply { isDaemon = true }
        }
        val customDispatcher = executor.asCoroutineDispatcher()
        val server = embeddedServer(
            CioServer,
            configure = {
                ioDispatcher = customDispatcher
                connector { port = 0 }
            }
        ) {
            routing {
                get("/") { call.respondText(Thread.currentThread().name) }
            }
        }

        server.startSuspend()
        try {
            val port = server.engine.resolvedConnectors().single().port
            HttpClient(CioClient).use { client ->
                val threadName = client.get("http://127.0.0.1:$port/").bodyAsText()
                assertTrue(
                    threadName.startsWith("cio-custom-io-thread"),
                    "Expected the call to run on the configured dispatcher, but was: $threadName"
                )
            }
        } finally {
            server.stopSuspend(100, 500)
            executor.shutdownNow()
        }
    }

    @Test
    fun `default ioDispatcher is not a custom thread`() = runBlocking {
        val server = embeddedServer(CioServer, configure = { connector { port = 0 } }) {
            routing {
                get("/") { call.respondText(Thread.currentThread().name) }
            }
        }

        server.startSuspend()
        try {
            val port = server.engine.resolvedConnectors().single().port
            HttpClient(CioClient).use { client ->
                val threadName = client.get("http://127.0.0.1:$port/").bodyAsText()
                assertEquals(false, threadName.startsWith("cio-custom-io-thread"))
            }
        } finally {
            server.stopSuspend(100, 500)
        }
    }
}
