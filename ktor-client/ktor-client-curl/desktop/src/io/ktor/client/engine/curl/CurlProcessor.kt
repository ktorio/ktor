/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import io.ktor.client.engine.curl.internal.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal class RequestContainer(
    val requestData: CurlRequestData,
    val completionHandler: CompletableDeferred<CurlSuccess>
)

/**
 * A class responsible for processing requests asynchronously.
 *
 * It holds a dispatcher interacting with curl multi interface API,
 * which requires API calls from single thread.
 */
internal class CurlProcessor(coroutineContext: CoroutineContext) {
    @OptIn(InternalAPI::class)
    private val curlDispatcher: CloseableCoroutineDispatcher =
        Dispatchers.createFixedThreadDispatcher("curl-dispatcher", 1)

    private var curlApi: CurlMultiApiHandler? by atomic(null)
    private val closed = atomic(false)

    private val curlScope = CoroutineScope(coroutineContext + curlDispatcher)
    private val requestQueue: Channel<RequestContainer> = Channel(Channel.UNLIMITED)
    private val requestCounter = atomic(0L)
    private val curlProtocols by lazy { getCurlProtocols() }

    init {
        val init = curlScope.launch {
            curlApi = CurlMultiApiHandler()
        }

        runBlocking {
            init.join()
        }

        runEventLoop()
    }

    suspend fun executeRequest(request: CurlRequestData): CurlSuccess {
        if (request.isUpgradeRequest && !curlProtocols.contains(request.protocol)) {
            error("WebSockets are supported in experimental libcurl 7.86 and greater")
        }

        val result = CompletableDeferred<CurlSuccess>()
        nextRequest {
            requestQueue.send(RequestContainer(request, result))
        }
        return result.await()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runEventLoop() {
        curlScope.launch {
            val api = curlApi!!
            while (!requestQueue.isClosedForReceive) {
                drainRequestQueue(api)
                api.perform(requestCounter)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun drainRequestQueue(api: CurlMultiApiHandler) {
        while (true) {
            val container = if (api.hasHandlers()) {
                requestQueue.tryReceive()
            } else {
                requestQueue.receiveCatching()
            }.getOrNull() ?: break

            val requestHandler = api.scheduleRequest(container.requestData, container.completionHandler)

            val requestCleaner = container.requestData.executionContext.invokeOnCompletion { cause ->
                if (cause == null) return@invokeOnCompletion
                cancelRequest(requestHandler, cause)
            }

            container.completionHandler.invokeOnCompletion {
                requestCleaner.dispose()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun close() {
        if (!closed.compareAndSet(false, true)) return

        requestQueue.close()
        nextRequest()

        GlobalScope.launch(curlDispatcher) {
            curlScope.coroutineContext[Job]!!.join()
            curlApi!!.close()
        }.invokeOnCompletion {
            curlDispatcher.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun cancelRequest(easyHandle: EasyHandle, cause: Throwable) {
        curlScope.launch {
            curlApi!!.cancelRequest(easyHandle, cause)
        }
    }

    private inline fun nextRequest(body: (Long) -> Unit = {}) = try {
        body(requestCounter.incrementAndGet())
    } finally {
        curlApi!!.wakeup()
    }
}
