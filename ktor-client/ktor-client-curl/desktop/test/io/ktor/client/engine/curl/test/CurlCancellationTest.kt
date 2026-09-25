/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.request.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CurlCancellationTest {

    @Test
    fun `cancelling a request aborts its transfer`() = runTest(timeout = 10.seconds) {
        withSilentServer { port, awaitRequest ->
            HttpClient(Curl).use { client ->
                val request = launch { client.get("http://127.0.0.1:$port/") }
                val connection = awaitRequest()

                request.cancel()
                connection.discard()
            }
        }
    }

    @Test
    fun `cancelling a request after the processor is closed does not throw`() = runTest(timeout = 10.seconds) {
        withSilentServer { port, awaitRequest ->
            val processorJob = Job()
            val processor = CurlProcessor(processorJob)
            val callJob = Job()
            val request = HttpRequestBuilder().apply { url("http://127.0.0.1:$port/") }.build()
                .toCurlRequest(CurlClientEngineConfig(), callJob)
            val execution = launch { processor.executeRequest(request) }
            awaitRequest()

            processor.close()
            processorJob.complete()
            processorJob.join()
            // Real time for the processor to close its dispatcher.
            withContext(Dispatchers.Default) { delay(500.milliseconds) }

            callJob.cancel()
            execution.cancel()
        }
    }

    private suspend fun withSilentServer(
        block: suspend (port: Int, awaitRequest: suspend () -> ByteReadChannel) -> Unit
    ) {
        SelectorManager().use { selector ->
            aSocket(selector).tcp().bind("127.0.0.1", 0).use { server ->
                val connections = mutableListOf<Socket>()
                try {
                    block((server.localAddress as InetSocketAddress).port) {
                        val connection = server.accept().also { connections += it }
                        connection.openReadChannel().also { it.readLine() }
                    }
                } finally {
                    connections.forEach { it.close() }
                }
            }
        }
    }
}
