/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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

    @Test
    fun `send after server close fails with rendezvous outgoing channel`() = runBlocking {
        assertSendAfterServerCloseFails(frames = 1) { outgoing = bounded(0) }
    }

    @Test
    fun `send after server close fails with bounded outgoing channel`() = runBlocking {
        assertSendAfterServerCloseFails(frames = 3) { outgoing = bounded(2) }
    }

    @Test
    fun `send after server close fails with default outgoing channel`() = runBlocking {
        assertSendAfterServerCloseFails(frames = 1) {}
    }

    @Test
    fun `server close is answered when writer is busy with rendezvous outgoing channel`() = runBlocking {
        withSession({ outgoing = bounded(0) }) { session, socket ->
            socket.onSend = { text ->
                if (text == "in flight") session.onClosing(socket, 1000, "End")
            }
            session.outgoing.send(Frame.Text("in flight"))

            socket.assertClosedWith(1000, "End")
            session.outgoing.assertSendFails(frames = 1)
        }
    }

    @Test
    fun `server close is answered when bounded outgoing channel is full`() = runBlocking {
        withSession({ outgoing = bounded(1) }) { session, socket ->
            socket.onSend = { text ->
                if (text == "in flight") {
                    session.outgoing.trySend(Frame.Text("queued"))
                    session.onClosing(socket, 1000, "End")
                }
            }
            session.outgoing.send(Frame.Text("in flight"))

            socket.assertClosedWith(1000, "End")
            assertEquals(listOf("in flight", "queued"), socket.sentTexts)
            session.outgoing.assertSendFails(frames = 1)
        }
    }
}

private const val TIMEOUT_MILLIS = 5_000L

private suspend fun assertSendAfterServerCloseFails(
    frames: Int,
    configureChannels: WebSocketChannelsConfig.() -> Unit,
) {
    withSession(configureChannels) { session, socket ->
        session.onClosing(socket, 1000, "End")

        socket.assertClosedWith(1000, "End")
        session.outgoing.assertSendFails(frames)
    }
}

private suspend fun FakeWebSocket.assertClosedWith(code: Int, reason: String) {
    val closed = withTimeoutOrNull(TIMEOUT_MILLIS) { closed.await() }
    assertEquals(code to reason, closed, "The client must answer the server's close")
}

private suspend fun SendChannel<Frame>.assertSendFails(frames: Int) = coroutineScope {
    var returned = 0
    val sending = async {
        runCatching {
            repeat(frames) {
                send(Frame.Text("after close $it"))
                returned++
            }
        }
    }
    val result = withTimeoutOrNull(TIMEOUT_MILLIS) { sending.await() }
    sending.cancel()

    assertNotNull(result, "A send after the writer has finished is still suspended")
    assertIs<CancellationException>(result.exceptionOrNull(), "A send after the writer has finished must fail")
    assertEquals(0, returned, "A frame sent after the writer has finished must not be queued")
}

private suspend fun withSession(
    configureChannels: WebSocketChannelsConfig.() -> Unit,
    block: suspend (OkHttpWebsocketSession, FakeWebSocket) -> Unit,
) {
    val job = Job()
    try {
        val socket = FakeWebSocket()
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://localhost").build()
        val wsConfig = WebSockets.prepare { channels(configureChannels) }
        val session = OkHttpWebsocketSession(
            client,
            WebSocket.Factory { _, _ -> socket },
            request,
            job + Dispatchers.Unconfined,
            wsConfig
        )
        session.start()
        block(session, socket)
    } finally {
        job.cancel()
    }
}

private class FakeWebSocket : WebSocket {
    val sentTexts = CopyOnWriteArrayList<String>()
    val closed = CompletableDeferred<Pair<Int, String?>>()
    var onSend: (String) -> Unit = {}

    override fun cancel() = Unit
    override fun close(code: Int, reason: String?): Boolean = closed.complete(code to reason)
    override fun queueSize(): Long = 0
    override fun request(): Request = Request.Builder().url("ws://localhost").build()
    override fun send(text: String): Boolean {
        sentTexts.add(text)
        onSend(text)
        return true
    }
    override fun send(bytes: ByteString): Boolean = true
}

private class FactoryUsedException : Exception()

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
    return withTimeout(5_000) { captured.await() }
}
