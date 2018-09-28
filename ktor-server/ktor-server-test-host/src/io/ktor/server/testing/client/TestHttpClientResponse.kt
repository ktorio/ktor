package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*


class TestHttpClientResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    contentData: ByteArray,
    override val coroutineContext: CoroutineContext
) : HttpResponse {
    override val requestTime = GMTDate()
    override val responseTime = GMTDate()
    override val version = HttpProtocolVersion.HTTP_1_1
    override val content: ByteReadChannel = ByteReadChannel(contentData)
}
