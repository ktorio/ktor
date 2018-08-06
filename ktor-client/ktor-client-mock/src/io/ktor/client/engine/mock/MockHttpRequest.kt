package io.ktor.client.engine.mock

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*

class MockHttpRequest(
    override val call: HttpClientCall,
    override val method: HttpMethod,
    override val url: Url,
    override val attributes: Attributes,
    override val executionContext: Job,
    override val content: OutgoingContent,
    override val headers: Headers
) : HttpRequest

fun HttpRequestData.toRequest(call: HttpClientCall): HttpRequest = io.ktor.client.engine.mock.MockHttpRequest(
    call, method, url, Attributes().apply(attributes), executionContext, body as OutgoingContent, headers
)

