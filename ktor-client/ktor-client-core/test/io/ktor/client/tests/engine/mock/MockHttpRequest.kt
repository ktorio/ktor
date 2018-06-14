package io.ktor.client.tests.engine.mock

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*

internal class MockHttpRequest(
    override val call: HttpClientCall,
    override val method: HttpMethod,
    override val url: Url,
    override val attributes: Attributes,
    override val executionContext: Job,
    override val content: OutgoingContent,
    override val headers: Headers
) : HttpRequest

internal fun HttpRequestData.toRequest(call: HttpClientCall): HttpRequest = MockHttpRequest(
    call, method, url, Attributes().apply(attributes), executionContext, body as OutgoingContent, headers
)

