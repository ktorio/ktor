package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*


internal class CIOHttpRequest(
    override val call: HttpClientCall,
    requestData: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = Attributes()
    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers
    override val content: OutgoingContent = requestData.body as OutgoingContent
    override val executionContext: CompletableDeferred<Unit> = requestData.executionContext
}
