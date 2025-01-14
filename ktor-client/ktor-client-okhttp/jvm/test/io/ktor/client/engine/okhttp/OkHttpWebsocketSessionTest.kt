/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
