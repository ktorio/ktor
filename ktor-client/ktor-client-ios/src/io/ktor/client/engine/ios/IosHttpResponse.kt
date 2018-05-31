package io.ktor.client.engine.ios

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*


class IosHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    override val content: ByteReadChannel,
    override val executionContext: Job,
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
) : HttpResponse {

    override fun close() {}
}