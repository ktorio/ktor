package io.ktor.client.engine.curl

import io.ktor.client.call.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import libcurl.*
import kotlin.coroutines.*

internal class CurlHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    override val requestTime: GMTDate,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext,
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
) : HttpResponse {
    override val responseTime: GMTDate = GMTDate()

    override fun close() {
        (coroutineContext[Job] as CompletableDeferred<Unit>).complete(Unit)
        super.close()
    }
}
