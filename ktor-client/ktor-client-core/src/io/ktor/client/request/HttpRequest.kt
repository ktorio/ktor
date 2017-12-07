package io.ktor.client.request

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import javax.net.ssl.*


interface HttpRequest : HttpMessage {
    val call: HttpClientCall

    val method: HttpMethod

    val url: Url

    val sslContext: SSLContext?

    val attributes: Attributes

    val executionContext: Job

    suspend fun execute(content: OutgoingContent): HttpResponse
}

class HttpRequestBuilder : HttpMessageBuilder {
    val url = UrlBuilder()
    var method = HttpMethod.Get
    override val headers = HeadersBuilder(caseInsensitiveKey = true)
    var body: Any = EmptyContent
    var sslContext: SSLContext? = null

    fun headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

    fun url(block: UrlBuilder.(UrlBuilder) -> Unit) = url.block(url)
}

fun HttpRequestBuilder.takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
    method = builder.method
    body = builder.body
    sslContext = builder.sslContext
    url.takeFrom(builder.url)
    headers.appendAll(builder.headers)

    return this
}
