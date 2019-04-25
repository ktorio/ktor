package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import kotlin.coroutines.*

internal suspend fun CloseableHttpAsyncClient.sendRequest(
    request: ApacheRequestProducer,
    callContext: CoroutineContext
): HttpResponseData {
    val response = CompletableDeferred<HttpResponseData>()
    val requestTime = GMTDate()

    val consumer = ApacheResponseConsumer(callContext) { rawResponse, body ->
        val statusLine = rawResponse.statusLine

        val status = HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
        val version = with(rawResponse.protocolVersion) { HttpProtocolVersion.fromValue(protocol, major, minor) }
        val headers = Headers.build {
            rawResponse.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }
        val result = HttpResponseData(status, requestTime, headers, version, body, callContext)
        response.complete(result)
    }

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {
            callContext.cancel()
            response.completeExceptionally(exception)
        }

        override fun completed(result: Unit) {}

        override fun cancelled() {
            callContext.cancel()
            response.cancel()
        }
    }

    response.invokeOnCompletion { cause ->
        cause ?: return@invokeOnCompletion
        callContext.cancel()
    }

    try {
        val future = execute(request, consumer, callback)
        response.invokeOnCompletion { cause ->
            cause ?: return@invokeOnCompletion
            future.cancel(true)
        }

    } catch (cause: Throwable) {
        response.completeExceptionally(cause)
        throw cause
    }

    return response.await()
}
