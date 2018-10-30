package io.ktor.client.engine.curl

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlin.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import kotlinx.cinterop.*
import libcurl.*
import platform.posix.size_t
import platform.posix.memcpy
import platform.posix.usleep

import io.ktor.client.engine.curl.temporary.*

class CurlClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {

    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    private val curlProcessor = CurlProcessor()

    init {
        curlProcessor.start()
        //launch(coroutineContext) {
        //    println("launching curlProcessor iteration")
        //    loop(curlProcessor)
        //}
    }

    // TODO: What we really want to do is to be able to reschedule next iteration after a delay.
    private val eventLoopKey = anEventLoop.subscribePeriodic { eventLoopIteration(curlProcessor) }

    override fun close() {
        anEventLoop.removePeriodic(eventLoopKey)
        curlProcessor.close()
        coroutineContext.cancel()
    }

    private val responseConsumers: MutableMap<CurlRequestData, (CurlResponseData) -> Unit> =
        mutableMapOf<CurlRequestData, (CurlResponseData) -> Unit>()

    private val listener = object : WorkerListener<CurlResponse> {
        override fun update(curlResponse: CurlResponse) {
            curlResponse.completeResponses.forEach {
                val consumer = responseConsumers[it.request]!!
                consumer(it)
            }
        }
    }

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall = suspendCancellableCoroutine { continuation ->
        val callContext = coroutineContext + CompletableDeferred<Unit>()
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val curlRequest = request.toCurlRequest()

        curlProcessor.addListener(curlRequest.listenerKey, listener)

        responseConsumers[curlRequest.newRequests.single()] = { curlResponseData ->

            val headers = curlResponseData.headers.parseResponseHeaders()

            val responseContext = writer(dispatcher, autoFlush = true) {

                for (chunk in curlResponseData.chunks) {
                    channel.writeFully(chunk, 0, chunk.size)
                }
            }

            val status = HttpStatusCode.fromValue(curlResponseData.status)

            val result = CurlHttpResponse(
                call, status, headers, requestTime,
                responseContext.channel, callContext, curlResponseData.version.fromCurl()
            )

            continuation.resume(HttpEngineCall(request, result))
        }

        curlProcessor.requestJob(curlRequest)
    }

    private fun eventLoopIteration(curlProcessor: CurlProcessor) {
        val key = ListenerKey()
        curlProcessor.requestJob(CurlRequest(emptySet(), key))
        curlProcessor.addListener(key, listener)
        curlProcessor.check(100)
    }

    private suspend fun loop(curlProcessor: CurlProcessor) {
        eventLoopIteration(curlProcessor)
        delay(300)
        loop(curlProcessor)
    }
}
