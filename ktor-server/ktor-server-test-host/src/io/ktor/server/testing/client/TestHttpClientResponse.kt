package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*


class TestHttpClientResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    contentData: ByteArray
) : HttpResponse {
    override val requestTime = GMTDate()
    override val responseTime = GMTDate()
    override val version = HttpProtocolVersion.HTTP_1_1
    override val content: ByteReadChannel = ByteReadChannel(contentData)

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    override fun close() {
        executionContext.complete(Unit)
        @Suppress("UNCHECKED_CAST")
        (call.request.executionContext as CompletableDeferred<Unit>).complete(Unit)
    }
}
