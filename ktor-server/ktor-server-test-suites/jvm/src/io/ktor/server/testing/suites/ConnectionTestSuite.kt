/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.net.*

public abstract class ConnectionTestSuite(public val engine: ApplicationEngineFactory<*, *>) {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    public fun testNetworkAddresses(): Unit = runBlocking {
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
            server.resolvedConnectors()
        }

        assertEquals(2, addresses.size)
        assertFalse(addresses.any { it.port == 0 })
        server.stop(50, 1000)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    public fun testServerReadyEvent(): Unit = runBlocking {
        val serverStarted = CompletableDeferred<Unit>()
        val serverPort = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
        val env = applicationEngineEnvironment {
            connector { port = serverPort }

            module {
                routing {
                    get("/") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        val server = embeddedServer(engine, env)

        server.environment.monitor.subscribe(ServerReady) {
            serverStarted.complete(Unit)
        }

        GlobalScope.launch {
            server.start(true)
        }

        withTimeout(5000) {
            serverStarted.join()
            val response = HttpClient(CIO).get("http://127.0.0.1:$serverPort/")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        server.stop(50, 100)
    }
}
