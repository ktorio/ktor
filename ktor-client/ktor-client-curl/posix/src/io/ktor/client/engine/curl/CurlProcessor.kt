package io.ktor.client.engine.curl

import io.ktor.client.engine.curl.internal.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*


/**
 * Only set in curl worker thread
 */
@ThreadLocal
private lateinit var curlApi: CurlMultiApiHandler

internal class CurlProcessor(
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    private val worker: Worker = Worker.start()
    private val responseConsumers: MutableMap<CurlRequestData, CompletableDeferred<CurlSuccess>> = mutableMapOf()
    private val closed = atomic(false)
    private val activeRequests = atomic(0)

    init {
        worker.execute(TransferMode.SAFE, { Unit }) {
            curlApi = CurlMultiApiHandler()
        }

        launch {
            while (!closed.value) {
                val futureResult = poll()

                while (futureResult.state == FutureState.SCHEDULED) delay(100)

                val result = futureResult.result
                processPoll(result)
            }
        }
    }

    suspend fun executeRequest(request: CurlRequestData): CurlSuccess {
        val deferred = CompletableDeferred<CurlSuccess>()
        responseConsumers[request] = deferred

        worker.execute(TransferMode.SAFE, { request.freeze() }, ::curlSchedule)
        activeRequests.incrementAndGet()

        return deferred.await()
    }

    fun close() {
        worker.execute(TransferMode.SAFE, { Unit }) { curlApi.close() }
    }

    private fun poll(): Future<List<CurlResponseData>> =
        worker.execute(TransferMode.SAFE, { Unit }, { pollCompleted() })

    private fun processPoll(result: List<CurlResponseData>) {
        result.forEach { response ->
            val task = responseConsumers[response.request]!!
            when (response) {
                is CurlSuccess -> task.complete(response)
                is CurlFail -> task.completeExceptionally(response.cause)
            }
            responseConsumers.remove(response.request)
        }

        activeRequests.update { it - result.size }
    }
}

internal fun curlSchedule(request: CurlRequestData) {
    curlApi.scheduleRequest(request)
}

internal fun pollCompleted(): List<CurlResponseData> = curlApi.pollCompleted(100).freeze()
