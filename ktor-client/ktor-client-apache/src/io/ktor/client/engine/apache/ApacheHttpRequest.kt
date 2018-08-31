package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import java.util.concurrent.atomic.*


internal class ApacheHttpRequest(
    override val call: HttpClientCall,
    private val engine: CloseableHttpAsyncClient,
    private val config: ApacheEngineConfig,
    private val requestData: HttpRequestData,
    private val dispatcher: CoroutineDispatcher
) : HttpRequest {
    override val attributes: Attributes = requestData.attributes

    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers
    override val content: OutgoingContent = requestData.body as OutgoingContent

    override val executionContext: CompletableDeferred<Unit> = requestData.executionContext

    suspend fun execute(): HttpResponse {
        val request = ApacheRequestProducer(requestData, config, content, dispatcher, executionContext)
        return engine.sendRequest(call, request, dispatcher)
    }
}

private suspend fun CloseableHttpAsyncClient.sendRequest(
    call: HttpClientCall,
    request: ApacheRequestProducer,
    dispatcher: CoroutineDispatcher
): ApacheHttpResponse {
    val response = CompletableDeferred<ApacheHttpResponse>()

    val completed = AtomicBoolean(false)
    val requestTime = GMTDate()
    val parent = CompletableDeferred<Unit>()

    val consumer = ApacheResponseConsumer(dispatcher, parent) { rawResponse, body ->
        if (completed.compareAndSet(false, true)) {
            val result = ApacheHttpResponse(call, requestTime, parent, rawResponse, body)
            response.complete(result)
        }
    }

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {
            parent.completeExceptionally(exception)
            if (completed.compareAndSet(false, true)) response.completeExceptionally(exception)
        }

        override fun completed(result: Unit) {}

        override fun cancelled() {
            parent.cancel()
            if (completed.compareAndSet(false, true)) response.cancel()
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
        parent.cancel(cause)
    }

    return response.await()
}
