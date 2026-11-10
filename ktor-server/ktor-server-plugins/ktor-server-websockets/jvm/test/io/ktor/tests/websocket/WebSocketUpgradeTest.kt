/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class WebSocketUpgradeTest {

    @Test
    fun `queued close frame is written on failure`() = testApplication {
        install(WebSockets)

        val written = CompletableDeferred<ByteArray>()
        routing {
            get("/") {
                val output = ByteChannel()
                val upgrade = WebSocketUpgrade(call) {
                    outgoing.trySend(Frame.Close(CloseReason(CloseReason.Codes.TOO_BIG, "")))
                    throw FrameTooBigException(1024)
                }

                // One thread queues the writer's resume behind the handler's failure.
                upgrade.upgrade(ByteChannel(), output, singleThread(), EmptyCoroutineContext).join()

                written.complete(output.readRemaining().readByteArray())
                call.respondText("done")
            }
        }

        client.get("/")

        val bytes = written.await()
        assertTrue(bytes.size >= 4, "Expected a Close frame, but the handler wrote ${bytes.size} bytes")
        assertEquals(0x88.toByte(), bytes[0], "Expected a Close frame first")
        val code = (bytes[2].toInt() and 0xff shl 8) or (bytes[3].toInt() and 0xff)
        assertEquals(CloseReason.Codes.TOO_BIG.code.toInt(), code)
    }

    @Test
    fun `failing handler continues when peer stops reading`() = testApplication {
        install(WebSockets) {
            timeoutMillis = 200
        }

        val finished = CompletableDeferred<Boolean>()
        routing {
            get("/") {
                val upgrade = WebSocketUpgrade(call) {
                    outgoing.send(Frame.Binary(true, ByteArray(1024 * 1024)))
                    outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.TOO_BIG, "")))
                    throw FrameTooBigException(1024)
                }

                val job = upgrade.upgrade(ByteChannel(), ByteChannel(), singleThread(), EmptyCoroutineContext)
                finished.complete(withTimeoutOrNull(5.seconds) { job.join() } != null)
                job.cancel()
                call.respondText("done")
            }
        }

        client.get("/")

        assertTrue(finished.await(), "The upgrade job did not finish while the output was not being read")
    }

    private fun singleThread() = Dispatchers.Default.limitedParallelism(1)
}
