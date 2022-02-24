/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.cio

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.test.*
import kotlin.time.*

class ClassCastExceptionTest : EngineTestBase<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableSsl = false
    }

    /**
     * Regression test for KTOR-349
     */
    @Test
    @NoHttp2
    @OptIn(ExperimentalTime::class)
    fun testClassCastException(): Unit = runBlocking {
        val exceptionHandler = CoroutineExceptionHandler { _, cause ->
            cancel("Uncaught failure", cause)
        }
        val server = embeddedServer(CIO, port = port, parentCoroutineContext = coroutineContext + exceptionHandler) {
            install(WebSockets)

            routing {
                get("/hang") {
                    suspendCancellableCoroutine {}
                }
            }
        }

        server.start()
        try {
            delay(1000L)

            launch {
                HttpClient(io.ktor.client.engine.cio.CIO).use { client ->
                    try {
                        client.get<String>(port = port, path = "/hang")
                    } catch (e: Throwable) {
                    }
                }
            }

            delay(1000L)
        } finally {
            server.stop(1L, 1L, TimeUnit.SECONDS)
        }
    }
}
