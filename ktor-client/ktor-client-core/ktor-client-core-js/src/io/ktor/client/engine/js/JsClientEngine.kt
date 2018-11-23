package io.ktor.client.engine.js

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.browser.*
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

        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val stream = rawResponse.body as? ReadableStream ?: error("Fail to obtain native stream: $call, $rawResponse")

        val response = JsHttpResponse(call, requestTime, rawResponse, stream.toByteChannel(callContext), callContext)
        HttpEngineCall(request, response)
    }

    override fun close() {
    }

}

