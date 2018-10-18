package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*


internal class ApacheHttpRequest(
    override val call: HttpClientCall,
    requestData: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = requestData.attributes

    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers
    override val content: OutgoingContent = requestData.body as OutgoingContent
}

internal suspend fun CloseableHttpAsyncClient.sendRequest(
    call: HttpClientCall,
    request: ApacheRequestProducer,
    callContext: CoroutineContext
): ApacheHttpResponse {
    val response = CompletableDeferred<ApacheHttpResponse>()
    val requestTime = GMTDate()

    val consumer = ApacheResponseConsumer(callContext) { rawResponse, body ->
        val result = ApacheHttpResponse(call, requestTime, rawResponse, body, callContext)
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

    val future = try {
        execute(request, consumer, callback)
    } catch (cause: Throwable) {
        response.completeExceptionally(cause)
        throw cause
    }

    response.invokeOnCompletion { cause ->
        cause ?: return@invokeOnCompletion
        future.cancel(true)
        callContext.cancel()
    }

    return response.await()
}
