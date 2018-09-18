package io.ktor.client.engine.jetty

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*

internal class JettyHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    override val requestTime: GMTDate,
    override val executionContext: CompletableDeferred<Unit>,
    override val content: ByteReadChannel,
    private val origin: Closeable
) : HttpResponse {
    override val version = HttpProtocolVersion.HTTP_2_0
    override val responseTime = GMTDate()

    override fun close() {
        origin.close()
        executionContext.complete(Unit)
    }
}