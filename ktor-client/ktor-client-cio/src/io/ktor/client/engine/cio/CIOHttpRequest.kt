package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*


class CIOHttpRequest(
        override val call: HttpClientCall,
        private val engine: CIOEngine,
        requestData: HttpRequestData
) : HttpRequest {
    override val attributes: Attributes = Attributes()
    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers
    override val content: OutgoingContent = requestData.body as OutgoingContent
    override val executionContext: CompletableDeferred<Unit> = requestData.executionContext
}
