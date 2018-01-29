package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import java.util.*
import java.util.concurrent.atomic.*


class ApacheHttpRequest(
        override val call: HttpClientCall,
        private val engine: CloseableHttpAsyncClient,
        private val config: ApacheEngineConfig,
        private val dispatcher: CoroutineDispatcher,
        private val requestData: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = Attributes()

    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers

    override val executionContext: CompletableDeferred<Unit> = requestData.executionContext

    override suspend fun execute(content: OutgoingContent): HttpResponse {
        val request = ApacheRequestProducer(requestData, config, content, dispatcher, executionContext)
        return engine.sendRequest(call, request, dispatcher)
    }
}

private suspend fun CloseableHttpAsyncClient.sendRequest(
        call: HttpClientCall,
        request: ApacheRequestProducer,
        dispatcher: CoroutineDispatcher
): ApacheHttpResponse = suspendCancellableCoroutine { continuation ->
    val completed = AtomicBoolean(false)
    val requestTime = Date()
    val parent = CompletableDeferred<Unit>()

    val consumer = ApacheResponseConsumer(dispatcher, parent) { response, body ->
        if (completed.compareAndSet(false, true)) {
            val result = ApacheHttpResponse(call, requestTime, parent, response, body)
            continuation.resume(result)
        }
    }

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {
            parent.completeExceptionally(exception)
            if (completed.compareAndSet(false, true)) continuation.resumeWithException(exception)
        }

        override fun completed(result: Unit) {}

        override fun cancelled() {
            parent.cancel()
            if (completed.compareAndSet(false, true)) continuation.cancel()
        }
    }

    val future = execute(request, consumer, callback)
    continuation.invokeOnCompletion(onCancelling = true) { cause ->
        cause ?: return@invokeOnCompletion
        future.cancel(true)
        parent.cancel(cause)
    }
}
