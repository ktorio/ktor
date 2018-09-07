package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*

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
fun HttpRequestData.toRequest(call: HttpClientCall): HttpRequest = MockHttpRequest(
    call, method, url, attributes, body as OutgoingContent, headers
)

