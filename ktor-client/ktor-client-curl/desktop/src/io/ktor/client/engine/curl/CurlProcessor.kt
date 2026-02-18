/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import io.ktor.client.engine.curl.CurlTask.*
import io.ktor.client.engine.curl.internal.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalForeignApi::class)
internal class CurlProcessor(coroutineContext: CoroutineContext) {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val curlDispatcher = newSingleThreadContext("curl-dispatcher")

    private var curlApi: CurlMultiApiHandler? by atomic(null)
    private val closed = atomic(false)

    private val curlScope = CoroutineScope(coroutineContext + curlDispatcher)
    private val taskQueue: Channel<CurlTask> = Channel(Channel.UNLIMITED)

    init {
        val init = curlScope.launch {
            curlApi = CurlMultiApiHandler()
        }

        runBlocking {
            init.join()
        }

        runEventLoop().invokeOnCompletion { cause ->
            cause?.let { curlScope.cancel(cause = CancellationException(cause)) }
        }
    }

    suspend fun executeRequest(request: CurlRequestData): CurlSuccess {
        val result = CompletableDeferred<CurlSuccess>()
        taskQueue.send(SendRequest(request, result))
        curlApi!!.wakeup()
        return result.await()
    }

    suspend fun sendWebSocketFrame(websocket: CurlWebSocketResponseBody, flags: Int, data: ByteArray) {
        val result = Job()
        taskQueue.send(SendWebSocketFrame(websocket, flags, data, result))
        curlApi!!.wakeup()
        result.join()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runEventLoop(): Job = curlScope.launch(CoroutineName("curl-processor-loop")) {
        memScoped {
            val transfersRunning = alloc<IntVar>()
            val api = curlApi!!
            while (!taskQueue.isClosedForReceive) {
                drainTaskQueue(api)
                api.perform(transfersRunning)
            }
        }
    }

    private suspend fun drainTaskQueue(api: CurlMultiApiHandler) {
        while (true) {
            val task = if (api.hasHandlers()) {
                taskQueue.tryReceive()
            } else {
                taskQueue.receiveCatching()
            }.getOrNull() ?: break

            when (task) {
                is SendRequest -> handleSendRequest(api, task)
                is SendWebSocketFrame ->
                    api.sendWebSocketFrame(task.websocket, task.flags, task.data, task.completionHandler)
            }
        }
    }

    private fun handleSendRequest(api: CurlMultiApiHandler, task: SendRequest) {
        val (requestData, completionHandler) = task
        val requestHandler = api.scheduleRequest(requestData, completionHandler)

        val requestCleaner = requestData.executionContext.invokeOnCompletion { cause ->
            if (cause == null) return@invokeOnCompletion
            cancelRequest(requestHandler, cause)
        }

        completionHandler.invokeOnCompletion {
            requestCleaner.dispose()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun close() {
        if (!closed.compareAndSet(false, true)) return

        taskQueue.close()
        curlApi!!.wakeup()

        GlobalScope.launch(curlDispatcher) {
            curlScope.coroutineContext[Job]!!.join()
            curlApi!!.close()
        }.invokeOnCompletion {
            curlDispatcher.close()
        }
    }

    private fun cancelRequest(easyHandle: EasyHandle, cause: Throwable) {
        curlScope.launch {
            curlApi!!.cancelRequest(easyHandle, cause)
        }
    }
}

private sealed interface CurlTask {

    data class SendRequest(
        val requestData: CurlRequestData,
        val completionHandler: CompletableDeferred<CurlSuccess>,
    ) : CurlTask

    class SendWebSocketFrame(
        val websocket: CurlWebSocketResponseBody,
        val flags: Int,
        val data: ByteArray,
        val completionHandler: CompletableJob,
    ) : CurlTask
}
