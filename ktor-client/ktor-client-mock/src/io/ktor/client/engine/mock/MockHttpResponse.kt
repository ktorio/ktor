package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*

class MockHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val content: ByteReadChannel = ByteReadChannel.Empty,
    override val headers: Headers = headersOf()
) : HttpResponse {
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
//    override val requestTime: Date = Date()
//    override val responseTime: Date = Date()
    override val executionContext: Job = Job()

    override fun close() {}
}