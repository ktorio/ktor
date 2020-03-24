/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import kotlinx.coroutines.*
import okhttp3.*
import java.lang.RuntimeException
import kotlin.test.*

class OkHttpWebsocketSessionTest {
    @Test
    fun testOnFailure() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com").build()
        val coroutineContext = Job()
        val session = OkHttpWebsocketSession(client, request, coroutineContext)
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
}
