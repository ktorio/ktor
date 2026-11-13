/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OkHttpWebsocketSessionTest {

    @Test
    fun testOnFailure() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com").build()
        val coroutineContext = Job()
        val session = OkHttpWebsocketSession(client, client, request, coroutineContext)
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val exception = RuntimeException()
        session.onFailure(webSocket, exception, null)

        val job = Job()
        session.closeReason.invokeOnCompletion { cause ->
            assertEquals(exception, cause)
            job.complete()
        }
        runBlocking { job.join() }
    }

    @Test
    fun `pingIntervalMillis is applied from ktor WebSockets config`() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://localhost").build()
        val session = OkHttpWebsocketSession(client, client, request, Job(), WebSockets(pingIntervalMillis = 15_000L))
        assertEquals(15_000L, session.pingIntervalMillis)
    }

    @Test
    fun `pingIntervalMillis falls back to engine value when ktor config is disabled`() {
        val pingInterval = 10_000L
        val client = OkHttpClient.Builder()
            .pingInterval(pingInterval, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url("ws://localhost").build()
        val session = OkHttpWebsocketSession(client, client, request, Job(), WebSockets())
        assertEquals(pingInterval, session.pingIntervalMillis)
    }

    @Test
    fun `engine value takes priority over ktor config`() {
        val engineInterval = 10_000L
        val ktorInterval = 5_000L
        val client = OkHttpClient.Builder()
            .pingInterval(engineInterval, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url("ws://localhost").build()
        val session =
            OkHttpWebsocketSession(client, client, request, Job(), WebSockets(pingIntervalMillis = ktorInterval))
        assertEquals(engineInterval, session.pingIntervalMillis)
    }

    @Test
    fun `pingIntervalMillis defaults to zero when nothing configured`() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://localhost").build()
        val session = OkHttpWebsocketSession(client, client, request, Job())
        assertEquals(0L, session.pingIntervalMillis)
    }

    @Test
    fun `Frame Close with application-private code is forwarded to OkHttp`() = runBlocking {
        val (code, reason) = captureCloseFor(CloseReason(4002.toShort(), "custom-disconnect"))
        assertEquals(4002, code)
        assertEquals("custom-disconnect", reason)
    }

    @Test
    fun `Frame Close with library-registered code is forwarded to OkHttp`() = runBlocking {
        val (code, reason) = captureCloseFor(CloseReason(3500.toShort(), "library-code"))
        assertEquals(3500, code)
        assertEquals("library-code", reason)
    }

    @Test
    fun `Frame Close with reserved code 1006 falls back to default`() = runBlocking {
        val (code, reason) = captureCloseFor(CloseReason(1006.toShort(), "should-be-replaced"))
        assertEquals(1011, code)
        assertEquals("Client failure", reason)
    }

    @Test
    fun `suspended send fails when the session is closed`() = runBlocking<Unit> {
        val socket = FakeWebSocket(blockSends = true)
        val session = sessionWithRendezvousOutgoing(socket)

        session.outgoing.send(Frame.Text("first"))
        socket.sendStarted.await()
        val late = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { session.outgoing.send(Frame.Text("late")) }
        }
        session.onClosed(socket, 1000, "bye")
        socket.releaseSends.countDown()

        val result = withTimeout(5.seconds) { late.await() }
        assertTrue(result.isFailure, "send() accepted a frame after the writer stopped")
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `server close is answered when the outgoing channel is full`() = runBlocking {
        val socket = FakeWebSocket(blockSends = true)
        val session = sessionWithRendezvousOutgoing(socket)

        launch { session.outgoing.send(Frame.Text("first")) }
        socket.sendStarted.await()
        session.onClosing(socket, 1000, "bye")
        socket.releaseSends.countDown()

        assertEquals(1000 to "bye", withTimeout(5.seconds) { socket.closed.await() })
    }

    @Test
    fun `send after onClosed fails with CancellationException`() = runBlocking {
        val socket = FakeWebSocket()
        val session = sessionWithRendezvousOutgoing(socket)

        session.onClosed(socket, 1000, "bye")

        withTimeout(5.seconds) {
            val cause = assertFailsWith<CancellationException> {
                session.outgoing.send(Frame.Text("late"))
            }
            assertEquals("WebSocket session closed with code NORMAL.", cause.message)
        }
    }

    @Test
    fun testWebSocketFactory() {
        val client = HttpClient(OkHttp) {
            install(WebSockets)

            engine {
                webSocketFactory = WebSocket.Factory { _, _ -> throw FactoryUsedException() }
            }
        }

        runBlocking {
            assertFailsWith<FactoryUsedException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                }
            }
        }
    }
}

private class FactoryUsedException : Exception()

private class FakeWebSocket(private val blockSends: Boolean = false) : WebSocket {
    val closed = CompletableDeferred<Pair<Int, String?>>()
    val sendStarted = CompletableDeferred<Unit>()
    val releaseSends = CountDownLatch(1)

    override fun cancel() = Unit
    override fun close(code: Int, reason: String?): Boolean = closed.complete(code to reason)
    override fun queueSize(): Long = 0
    override fun request(): Request = Request.Builder().url("ws://localhost").build()
    override fun send(text: String): Boolean = onSend()
    override fun send(bytes: ByteString): Boolean = onSend()

    private fun onSend(): Boolean {
        sendStarted.complete(Unit)
        if (blockSends) releaseSends.await(5, TimeUnit.SECONDS)
        return true
    }
}

private fun sessionWithRendezvousOutgoing(socket: WebSocket): OkHttpWebsocketSession {
    val client = OkHttpClient()
    val request = Request.Builder().url("ws://localhost").build()
    val wsConfig = WebSockets.prepare { channels { outgoing = bounded(0) } }
    return OkHttpWebsocketSession(client, { _, _ -> socket }, request, Job(), wsConfig).apply { start() }
}

private suspend fun captureCloseFor(reason: CloseReason): Pair<Int, String?> {
    val captured = CompletableDeferred<Pair<Int, String?>>()
    val socket = object : WebSocket {
        override fun cancel() = Unit
        override fun close(code: Int, reason: String?): Boolean {
            captured.complete(code to reason)
            return true
        }
        override fun queueSize(): Long = 0
        override fun request(): Request = Request.Builder().url("ws://localhost").build()
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
    }
    val factory = WebSocket.Factory { _, _ -> socket }
    val client = OkHttpClient()
    val request = Request.Builder().url("ws://localhost").build()
    val session = OkHttpWebsocketSession(client, factory, request, Job())
    session.start()
    session.outgoing.send(Frame.Close(reason))
    return withTimeout(5.seconds) { captured.await() }
}
