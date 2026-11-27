/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Makes a test request that sets up a WebSocket session and invokes the [callback] function
 * that handles conversation with the server
 */
@OptIn(DelicateCoroutinesApi::class)
internal suspend fun TestApplicationEngine.handleWebSocketConversation(
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {},
    awaitCallback: Boolean = true,
    callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
): TestApplicationCall {
    val websocketChannel = ByteChannel(true)
    val call = createWebSocketCall(uri) {
        setup()
        bodyChannel = websocketChannel
    }
    val response = call.response

    launch(configuration.dispatcher) {
        try {
            pipeline.execute(call)
        } catch (cause: Throwable) {
            response.responseChannel.completeExceptionally(cause)
        }
    }.invokeOnCompletion { cause ->
        if (cause == null) {
            response.responseChannel.complete(null)
        } else {
            response.responseChannel.completeExceptionally(cause)
        }
    }

    withContext(configuration.dispatcher) {
        val responseChannel = response.responseChannel.await()
            ?: error("WebSocket connection failed")
        processResponse(call)
        val connectionEstablished = withTimeoutOrNull(1000.milliseconds) {
            response.webSocketEstablished.join()
        }
        if (connectionEstablished == null) {
            error("WebSocket connection failed")
        }

        // A child of the engine, so stopping the engine ends a conversation whose server never closes.
        val job = Job(this@handleWebSocketConversation.coroutineContext[Job])
        val webSocketContext = Dispatchers.Unconfined + job
        val pool = KtorDefaultPool
        val writer =
            WebSocketWriter(websocketChannel, webSocketContext, pool = pool, queueConfig = ChannelConfig.UNLIMITED)
        val reader = WebSocketReader(
            responseChannel,
            webSocketContext,
            Int.MAX_VALUE.toLong(),
            pool,
            queueConfig = ChannelConfig.UNLIMITED
        )

        val scope = if (awaitCallback) this else GlobalScope
        scope.launch {
            try {
                // execute client side
                call.callback(reader.incoming, writer.outgoing)
            } finally {
                writer.flush()
                writer.outgoing.close()
                // Keep reading until the server closes, so its reply to our Close still arrives.
                job.complete()
            }
        }
    }

    return call
}
