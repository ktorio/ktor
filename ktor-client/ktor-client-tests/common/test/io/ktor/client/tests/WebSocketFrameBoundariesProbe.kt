/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.test.base.EngineSelectionRule.Companion.except
import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prints the frames each engine emits on a raw (non-defragmenting) session
 * for messages that the server sends as known wire frames.
 */
class WebSocketFrameBoundariesProbe : ClientLoader() {

    private class Scenario(val type: String, val sizes: List<Int>, val delayMs: Int)

    private val scenarios = listOf(
        Scenario("binary", listOf(100, 100, 100), delayMs = 0),
        Scenario("binary", listOf(100, 100, 100), delayMs = 200),
        Scenario("binary", listOf(3000, 3000), delayMs = 0),
        Scenario("binary", listOf(10000), delayMs = 0),
        Scenario("binary", listOf(40000), delayMs = 0),
        Scenario("text", listOf(100, 100, 100), delayMs = 0),
        Scenario("text", listOf(10000), delayMs = 0),
    )

    @Test
    fun `print received frame boundaries`() = clientTests(except(ENGINES_WITHOUT_WS), retries = 0) {
        config {
            install(WebSockets)
        }

        test { client ->
            val engine = client.engine::class.simpleName
            for (scenario in scenarios) {
                val query = "sizes=${scenario.sizes.joinToString(",")}&type=${scenario.type}&delayMs=${scenario.delayMs}"
                val received = mutableListOf<Frame>()
                client.prepareRequest("$TEST_WEBSOCKET_SERVER/websockets/fragments?$query")
                    .body<ClientWebSocketSession, Unit> { session ->
                        for (frame in session.incoming) {
                            received += frame
                            if (frame is Frame.Close) {
                                session.outgoing.trySend(Frame.Close())
                                break
                            }
                        }
                    }

                val dataFrames = received.filter { it is Frame.Text || it is Frame.Binary }
                val description = received.joinToString(" ") { "${it.frameType}(${it.data.size},fin=${it.fin})" }
                println("PROBE [$engine] sent=${scenario.type}${scenario.sizes} delay=${scenario.delayMs}ms -> $description")
                assertEquals(scenario.sizes.sum(), dataFrames.sumOf { it.data.size }, "Payload size mismatch")
            }
        }
    }
}
