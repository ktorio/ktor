/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.internal.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
internal class DarwinClientEngine(override val config: DarwinClientEngineConfig) : HttpClientEngineBase("ktor-darwin") {
    private val requestQueue = NSOperationQueue()

    override val dispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeout, WebSocketCapability)

    private val session = DarwinSession(config, requestQueue)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        if (data.isUpgradeRequest()) {
            return executeWebSocketRequest(data, callContext)
        }

        return session.execute(data, callContext)
    }

    @OptIn(UnsafeNumber::class)
    private suspend fun executeWebSocketRequest(
        data: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        val request = data.toNSUrlRequest()
            .apply(config.requestConfig)

        val websocketSession = DarwinWebsocketSession(GMTDate(), callContext, config.challengeHandler)
        val session = createSession(config, websocketSession.delegate, requestQueue)
        val task = session.webSocketTaskWithRequest(request)
        websocketSession.task = task

        launch(callContext) {
            task.resume()
        }

        return try {
            websocketSession.response.await()
        } catch (cause: CancellationException) {
            if (task.state == NSURLSessionTaskStateRunning) {
                task.cancel()
            }
            throw cause
        }
    }
}
