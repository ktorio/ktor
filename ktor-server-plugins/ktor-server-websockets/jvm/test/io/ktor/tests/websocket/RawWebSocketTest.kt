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
import org.junit.rules.*
import java.io.*
import kotlin.reflect.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RawWebSocketTest {
    @get:Rule
    val timeout: CoroutinesTimeout = CoroutinesTimeout.seconds(10, true)

    @get:Rule
    val test: TestName = TestName()

    @get:Rule
    val errors = ErrorCollector()

    private lateinit var parent: CompletableJob
    private lateinit var client2server: ByteChannel
    private lateinit var server2client: ByteChannel

    private lateinit var server: RawWebSocket

    private lateinit var client: RawWebSocket

    private val exceptionHandler = CoroutineExceptionHandler { _, cause ->
        if (cause !is CancellationException && cause !is PlannedIOException) {
            errors.addError(cause)
        }
    }

    @BeforeTest
    fun prepare() {
        parent = Job()
        client2server = ByteChannel()
        server2client = ByteChannel()

        server = RawWebSocket(client2server, server2client, coroutineContext = parent + exceptionHandler)
        client = RawWebSocket(server2client, client2server, coroutineContext = parent + exceptionHandler)
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
    fun smokeTest(): Unit = runTest {
        val text = "smoke"
        client.send(Frame.Ping(text.toByteArray()))

        val receivedPing = server.incoming.receive()
        assertEquals(FrameType.PING, receivedPing.frameType)
        assertEquals(text, receivedPing.readBytes().toString(Charsets.ISO_8859_1))

        server.send(Frame.Pong(text.toByteArray()))
        val receivedPong = client.incoming.receive()
        assertEquals(FrameType.PONG, receivedPong.frameType)
        assertEquals(text, receivedPong.readBytes().toString(Charsets.ISO_8859_1))

        client.cancel()
        server.cancel()

        ensureCompletion()
    }

    @Test
    fun testServerIncomingDisconnected(): Unit = runTest {
        client2server.close()
        assertNull(server.incoming.receiveOrNull())
        server.outgoing.send(Frame.Close())

        client.incoming.receiveOrNull() as Frame.Close
        client.cancel()

        ensureCompletion()
    }

    @Test
    fun testServerIncomingConnectionLoss(): Unit = runTest {
        client2server.close(PlannedIOException())
        ensureCompletion(allowedExceptionsFromIncoming = listOf(IOException::class))
    }

    @Test
    fun testCloseSequenceInitiatedByClient(): Unit = runTest {
        val text = "content"

        client.send(Frame.Text(text))
        val textFrame = server.incoming.receive() as Frame.Text
        assertEquals(text, textFrame.readText())

        client.close()

        val clientCloseFrame = server.incoming.receive() as Frame.Close
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, ""), clientCloseFrame.readReason())

        server.close(clientCloseFrame.readReason()!!)

        val serverCloseFrame = client.incoming.receive()
        assertTrue { serverCloseFrame is Frame.Close }

        ensureCompletion()
    }

    @Test
    fun testSendToClosed(): Unit = runTest {
        cancelAtIncomingEnd(server)
        client.close()
        ensureCompletion()

        assertFailsWith<CancellationException> {
            server.outgoing.send(Frame.Close())
        }
        assertFailsWith<ClosedSendChannelException> {
            client.outgoing.send(Frame.Close())
        }

        Unit
    }

    @Test
    fun testCloseSequenceInitiatedByClientNoMessages(): Unit = runTest {
        cancelAtIncomingEnd(server)
        client.close()
        ensureCompletion()
    }

    @Test
    fun testParentCancellation(): Unit = runTest {
        parent.cancel()
        ensureCompletion()
    }

    @Test
    fun testServerTerminate(): Unit = runTest {
        cancelAtIncomingEnd(client)
        server.cancel()
        ensureCompletion()
    }

    private suspend fun ensureCompletion(allowedExceptionsFromIncoming: List<KClass<out Throwable>> = emptyList()) {
        parent.complete()
        parent.join()

        assertTrue("client -> server channel should be closed") { client2server.isClosedForRead }
        assertTrue("client -> server channel should be closed") { client2server.isClosedForWrite }

        assertTrue("server -> client channel should be closed") { server2client.isClosedForRead }
        assertTrue("server -> client channel should be closed") { server2client.isClosedForWrite }

        try {
            server.incoming.consumeEach {}
        } catch (_: CancellationException) {
        } catch (other: Throwable) {
            other.assertOnOf(allowedExceptionsFromIncoming)
        }

        try {
            client.incoming.consumeEach {}
        } catch (_: CancellationException) {
        } catch (other: Throwable) {
            other.assertOnOf(allowedExceptionsFromIncoming)
        }

        assertTrue("client incoming should be closed") { client.incoming.isClosedForReceive }
        assertTrue("server incoming should be closed") { server.incoming.isClosedForReceive }

        assertTrue("client outgoing should be closed") { client.outgoing.isClosedForSend }
        assertTrue("server outgoing should be closed") { server.outgoing.isClosedForSend }
    }

    private fun Throwable.assertOnOf(
        exceptions: Collection<KClass<out Throwable>>
    ) {
        if (exceptions.none { it.isInstance(this) }) {
            throw this
        }
    }

    private fun CoroutineScope.cancelAtIncomingEnd(side: WebSocketSession) {
        launch {
            side.incoming.consumeEach {}
            side.cancel()
        }
    }

    private fun runTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking(CoroutineName("test-${test.methodName}")) {
            block()
        }
    }

    private class PlannedIOException : IOException("Connection loss.")
}
