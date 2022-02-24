/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWebSocketTest {
    @get:Rule
    val timeout: CoroutinesTimeout = CoroutinesTimeout.seconds(10, true)

    private lateinit var parent: CompletableJob
    private lateinit var client2server: ByteChannel
    private lateinit var server2client: ByteChannel

    private lateinit var server: DefaultWebSocketSession

    private lateinit var client: RawWebSocket

    @BeforeTest
    fun prepare() {
        parent = Job()
        client2server = ByteChannel()
        server2client = ByteChannel()

        server = DefaultWebSocketSession(
            RawWebSocket(client2server, server2client, coroutineContext = parent),
            -1L,
            1000L
        )
        server.start()

        client = RawWebSocket(server2client, client2server, coroutineContext = parent)
    }

    @AfterTest
    fun cleanup() {
        server.cancel()
        client.cancel()
        client2server.cancel()
        server2client.cancel()
        parent.cancel()
    }

    @Test
    fun closeByClient(): Unit = runBlocking {
        val reason = CloseReason(CloseReason.Codes.NORMAL, "test1")

        client.close(reason)
        assertEquals(reason, server.closeReason.await())

        // server for sure received a close frame so it should reply with a duplicate close frame
        // so we should be able to receive it at client side

        val closed = client.incoming.receive() as Frame.Close
        assertEquals(reason, closed.readReason())

        ensureCompletion()
    }

    @Test
    fun pingPong(): Unit = runBlocking {
        val pingsMessages = (1..5).map { "ping $it" }

        pingsMessages.forEach {
            client.send(Frame.Ping(it.toByteArray()))
        }
        pingsMessages.forEach {
            assertEquals(it, (client.incoming.receive() as Frame.Pong).readBytes().toString(Charsets.UTF_8))
        }

        client.close()
        assertTrue(client.incoming.receive() is Frame.Close)

        ensureCompletion()
    }

    @Test
    fun testCancellation(): Unit = runBlocking {
        server.cancel()

        client.incoming.receiveOrNull()
        client.close()

        ensureCompletion()
    }

    private suspend fun ensureCompletion() {
        parent.complete()
        parent.join()

        assertTrue("client -> server channel should be closed") { client2server.isClosedForRead }
        assertTrue("client -> server channel should be closed") { client2server.isClosedForWrite }

        assertTrue("server -> client channel should be closed") { server2client.isClosedForRead }
        assertTrue("server -> client channel should be closed") { server2client.isClosedForWrite }

        try {
            server.incoming.consumeEach {
                assertTrue("It should be no control frames") { !it.frameType.controlFrame }
            }
        } catch (_: CancellationException) {
        }

        try {
            client.incoming.consumeEach {}
        } catch (_: CancellationException) {
        }

        assertTrue("client incoming should be closed") { client.incoming.isClosedForReceive }
        assertTrue("server incoming should be closed") { server.incoming.isClosedForReceive }

        assertTrue("client outgoing should be closed") { client.outgoing.isClosedForSend }
        assertTrue("server outgoing should be closed") { server.outgoing.isClosedForSend }
    }
}
