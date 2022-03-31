/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.*

internal class DarwinClientEngine(override val config: DarwinClientEngineConfig) : HttpClientEngineBase("ktor-darwin") {

    override val dispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeout, WebSocketCapability)

    @OptIn(InternalAPI::class, UnsafeNumber::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val url = URLBuilder().takeFrom(data.url).buildString()

        val nativeRequest = NSMutableURLRequest.requestWithURL(NSURL(string = url)).apply {
            setupSocketTimeout(data)

            val content = data.body
            content.toNSData()?.let {
                setHTTPBody(it)
            }

            mergeHeaders(data.headers, data.body) { key, value ->
                setValue(value, key)
            }

            setCachePolicy(NSURLRequestReloadIgnoringCacheData)
            setHTTPMethod(data.method.value)

            config.requestConfig(this)
        }

        if (data.isUpgradeRequest()) {
            return executeWebSocketRequest(nativeRequest, callContext)
        }

        return executeRequest(data, nativeRequest, callContext)
    }

    @OptIn(UnsafeNumber::class)
    private suspend fun executeWebSocketRequest(
        nativeRequest: NSMutableURLRequest,
        callContext: CoroutineContext
    ): HttpResponseData {
        val websocketSession = DarwinWebsocketSession(GMTDate(), callContext)
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            websocketSession.delegate,
            NSOperationQueue.currentQueue()
        )
        val task = session.webSocketTaskWithRequest(nativeRequest)
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

    @OptIn(UnsafeNumber::class)
    private suspend fun executeRequest(
        data: HttpRequestData,
        nativeRequest: NSMutableURLRequest,
        callContext: CoroutineContext
    ): HttpResponseData {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            setupProxy(config)
            config.sessionConfig(this)
        }
        val responseReader = DarwinResponseReader(callContext, data, config)
        val session = NSURLSession.sessionWithConfiguration(
            configuration,
            responseReader,
            delegateQueue = NSOperationQueue.currentQueue()
        )

        val task = session.dataTaskWithRequest(nativeRequest)

        launch(callContext) {
            task.resume()
        }

        callContext[Job]!!.invokeOnCompletion {
            session.finishTasksAndInvalidate()
        }

        return try {
            responseReader.awaitResponse()
        } catch (cause: CancellationException) {
            if (task.state == NSURLSessionTaskStateRunning) {
                task.cancel()
            }
            throw cause
        }
    }
}
