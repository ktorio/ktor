package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*

class TestHttpClientRequest(
        override val call: HttpClientCall,
        private val engine: TestHttpClientEngine,
        data: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = Attributes()

    override val method: HttpMethod = data.method
    override val url: Url = data.url
    override val headers: Headers = data.headers

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun execute(content: OutgoingContent): HttpResponse {
        val response = engine.runRequest(method, url.fullPath, headers, content).response
        return TestHttpClientResponse(call, response.status()!!, response.headers.allValues(), response.byteContent!!)
    }
}
