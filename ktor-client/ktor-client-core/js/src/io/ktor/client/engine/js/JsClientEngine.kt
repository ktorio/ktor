package io.ktor.client.engine.js

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.js.compatible.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.w3c.fetch.*
import kotlin.coroutines.*

internal class JsClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {
    private val utils by lazy { Utils.get() }

    override val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
        call: HttpClientCall, data: HttpRequestData
    ): HttpEngineCall = withContext(dispatcher) {
        val callContext = CompletableDeferred<Unit>(this@JsClientEngine.coroutineContext[Job]) + dispatcher

        val requestTime = GMTDate()
        val request = DefaultHttpRequest(call, data)
        val rawResponse = fetch(request.url, CoroutineScope(callContext).toRaw(request))

        val response = JsHttpResponse(
            call,
            requestTime,
            rawResponse,
            utils.getBodyContentAsChannel(rawResponse, callContext),
            callContext
        )
        HttpEngineCall(request, response)
    }

    override fun close() {
    }

    private suspend fun fetch(url: Url, request: RequestInit): Response = suspendCancellableCoroutine {
        utils.fetch(url.toString(), request).then({ response ->
            it.resume(response)
        }, { cause ->
            it.resumeWithException(JsError(cause))
        })
    }
}

/**
 * Wrapper for javascript `error` objects.
 * @property origin: fail reason
 */
class JsError(val origin: dynamic) : Throwable("Error from javascript[$origin].")
