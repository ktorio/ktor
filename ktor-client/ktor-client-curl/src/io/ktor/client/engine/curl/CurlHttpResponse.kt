package io.ktor.client.engine.curl

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*
import libcurl.*

class CurlHttpResponse(
    override val call: HttpClientCall,
    override val status: HttpStatusCode,
    override val headers: Headers,
    override val requestTime: GMTDate,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext,
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
) : HttpResponse {

    override val responseTime: GMTDate = GMTDate()
}

fun UInt.fromCurl(): HttpProtocolVersion = when (this) {
    CURL_HTTP_VERSION_1_0 -> HttpProtocolVersion.HTTP_1_0
    CURL_HTTP_VERSION_1_1 -> HttpProtocolVersion.HTTP_1_1
    CURL_HTTP_VERSION_2_0 -> HttpProtocolVersion.HTTP_2_0
    CURL_HTTP_VERSION_2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    else -> throw CurlUnsupportedProtocolException(this)
}
