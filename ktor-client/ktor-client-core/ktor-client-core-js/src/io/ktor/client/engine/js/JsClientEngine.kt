package io.ktor.client.engine.js

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.js.compatible.Utils
import io.ktor.client.request.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

class JsClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
            call: HttpClientCall, data: HttpRequestData
    ): HttpEngineCall = withContext(dispatcher) {
        val callContext = CompletableDeferred<Unit>(this@JsClientEngine.coroutineContext[Job]) + dispatcher

        val requestTime = GMTDate()
        val request = DefaultHttpRequest(call, data)
        val rawResponse = fetch(request.url, CoroutineScope(callContext).toRaw(request))

        val response = JsHttpResponse(call,
                requestTime,
                rawResponse,
                Utils.get().getBodyContentAsChannel(rawResponse, callContext),
                callContext)
        HttpEngineCall(request, response)
    }

    override fun close() {
    }

}

