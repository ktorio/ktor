/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.test.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultWebSocketTestJvm {

    @OptIn(InternalAPI::class)
    @Test
    fun testOutgoingCapacity(): Unit = runTest {
        val capacity = 1000 // more than BBC can buffer

        System.setProperty("io.ktor.websocket.outgoingChannelCapacity", "$capacity")

        val parent = Job()
        val client2server = ByteChannel()
        val server2client = ByteChannel()

        val server = DefaultWebSocketSession(
            RawWebSocket(client2server, server2client, coroutineContext = parent),
            PINGER_DISABLED,
            timeoutMillis = 1_000L,
        ).apply { start() }

        val client = RawWebSocket(server2client, client2server, coroutineContext = parent)

        repeat(capacity) {
            server.outgoing.send(Frame.Text("$it"))
        }
        repeat(capacity) {
            assertEquals("$it", (client.incoming.receive() as Frame.Text).readText())
        }

        client.close()
        server.closeReason.await()
        client.incoming.receive()
        ensureCompletion(parent, client2server, server2client, server, client)

        System.clearProperty("io.ktor.websocket.outgoingChannelCapacity")

        server.cancel()
        client.cancel()
        client2server.cancel()
        server2client.cancel()
        parent.cancel()
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testMaxFrameSizeCheckDisabled(): Unit = runTest {
        val parent = Job()
        val client2server = ByteChannel()
        val server2client = ByteChannel()

        val server = DefaultWebSocketSession(
            session = object : WebSocketSession by RawWebSocket(
                input = client2server,
                output = server2client,
                coroutineContext = parent
            ) {
                // this should disable max frame size check in DefaultWebSocketSession
                override fun checksMaxMessageSize(): Boolean = true
            },
            pingIntervalMillis = PINGER_DISABLED,
            timeoutMillis = 1_000L,
            channelsConfig = WebSocketChannelsConfig.UNLIMITED,
        )
        server.maxFrameSize = 10
        server.start()

        val client = RawWebSocket(input = server2client, output = client2server, coroutineContext = parent)
        // Each frame fits into the limit, only the reassembled message exceeds it
        client.send(Frame.Binary(fin = false, ByteArray(6)))
        client.send(Frame.Binary(fin = true, ByteArray(6)))

        assertEquals(12, server.incoming.receive().data.size)

        server.cancel()
        client.cancel()
        client2server.cancel()
        server2client.cancel()
        parent.cancel()
    }
}
