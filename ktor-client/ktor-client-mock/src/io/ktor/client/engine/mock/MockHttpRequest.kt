package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

class MockHttpRequest(
    override val call: HttpClientCall,
    override val method: HttpMethod,
    override val url: Url,
    override val attributes: Attributes,
    override val content: OutgoingContent,
    override val headers: Headers
) : HttpRequest

/**
 * Convert [HttpRequestData] to [MockHttpRequest]
 */
fun HttpRequestData.toRequest(call: HttpClientCall): MockHttpRequest = io.ktor.client.engine.mock.MockHttpRequest(
    call, method, url, attributes, body as OutgoingContent, headers
)

fun MockHttpRequest.responseError(
    status: HttpStatusCode,
    content: String = status.description,
    headers: Headers = headersOf()
): MockHttpResponse = response(content, status, headers)

fun MockHttpRequest.responseOk(
    content: String = ""
): MockHttpResponse = response(content, HttpStatusCode.OK)

fun MockHttpRequest.response(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): MockHttpResponse = response(ByteReadChannel(content.toByteArray(Charsets.UTF_8)), status, headers)

fun MockHttpRequest.response(
    content: ByteArray,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): MockHttpResponse = response(ByteReadChannel(content), status, headers)

fun MockHttpRequest.response(
    content: ByteReadChannel,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: Headers = headersOf()
): MockHttpResponse = MockHttpResponse(call, status, content, headers)
