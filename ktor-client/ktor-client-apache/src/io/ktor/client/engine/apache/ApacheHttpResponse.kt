package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.client.methods.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import org.apache.http.nio.client.methods.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*


class ApacheHttpResponse internal constructor(
        override val call: HttpClientCall,
        override val requestTime: Date,
        override val executionContext: CompletableDeferred<Unit>,
        private val engineResponse: org.apache.http.HttpResponse,
        private val content: ByteReadChannel
) : HttpResponse {
    override val status: HttpStatusCode
    override val version: HttpProtocolVersion
    override val headers: Headers
    override val responseTime: Date = Date()

    init {
        val (code, reason) = with(engineResponse.statusLine) { statusCode to reasonPhrase }

        status = if (reason != null) HttpStatusCode(code, reason) else HttpStatusCode.fromValue(code)
        version = with(engineResponse.protocolVersion) { HttpProtocolVersion(protocol, major, minor) }
        headers = HeadersBuilder().apply {
            engineResponse.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }.build()
    }

    override fun receiveContent(): IncomingContent = object : IncomingContent {
        override val headers: Headers = this@ApacheHttpResponse.headers

        override fun readChannel(): ByteReadChannel = content

        override fun multiPartData(): MultiPartData = throw UnsupportedOperationException()
    }

    override fun close() {
        executionContext.complete(Unit)
    }
}

internal suspend fun CloseableHttpAsyncClient.sendRequest(
        call: HttpClientCall,
        request: HttpUriRequest,
        dispatcher: CoroutineDispatcher
): ApacheHttpResponse = suspendCoroutine { continuation ->
    val content = ByteChannel()
    val completed = AtomicBoolean(false)
    val requestTime = Date()
    val parent = CompletableDeferred<Unit>()

    val consumer = ApacheResponseConsumer(content, dispatcher, parent) {
        if (completed.compareAndSet(false, true)) {
            val result = ApacheHttpResponse(call, requestTime, parent, it, content)
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
            val cause = CancellationException("ApacheBackend: request canceled")
            parent.cancel()
            if (completed.compareAndSet(false, true)) continuation.resumeWithException(cause)
        }
    }

    execute(HttpAsyncMethods.create(request), consumer, callback)
}
