/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing

import io.ktor.http.cio.websocket.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * Make a test request that setup a websocket session and invoke [callback] function
 * that does conversation with server
 */
@OptIn(WebSocketInternalAPI::class)
public fun TestApplicationEngine.handleWebSocketConversation(
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {},
    callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
): TestApplicationCall {
    val websocketChannel = ByteChannel(true)
    val call = runBlocking {
        createWebSocketCall(uri) {
            setup()
            bodyChannel = websocketChannel
        }
    }

    // we need this to wait for response channel appearance
    // otherwise we get NPE at websocket reader start attempt
    val responseSent: CompletableJob = Job()
    call.response.responseChannelDeferred.invokeOnCompletion { cause ->
        when (cause) {
            null -> responseSent.complete()
            else -> responseSent.completeExceptionally(cause)
        }
    }

    launch(configuration.dispatcher) {
        try {
            // execute server-side
            pipeline.execute(call)
        } catch (t: Throwable) {
            responseSent.completeExceptionally(t)
            throw t
        }
    }

    val pool = KtorDefaultPool
    val engineContext = Dispatchers.Unconfined
    val job = Job()
    val webSocketContext = engineContext + job

    runBlocking(configuration.dispatcher) {
        responseSent.join()
        processResponse(call)

        val writer = WebSocketWriter(websocketChannel, webSocketContext, pool = pool)
        val responseChannel = call.response.websocketChannel()!!
        val reader = WebSocketReader(responseChannel, webSocketContext, Int.MAX_VALUE.toLong(), pool)

        try {
            // execute client side
            call.callback(reader.incoming, writer.outgoing)
        } finally {
            writer.flush()
            writer.outgoing.close()
            job.cancelAndJoin()
        }
    }

    return call
}
