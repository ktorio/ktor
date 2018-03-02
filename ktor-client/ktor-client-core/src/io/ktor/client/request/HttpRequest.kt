package io.ktor.client.request

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*


interface HttpRequest : HttpMessage {
    val call: HttpClientCall

    val method: HttpMethod

    val url: Url

    val attributes: Attributes

    val executionContext: Job

    suspend fun execute(content: OutgoingContent): HttpResponse
}

class HttpRequestBuilder : HttpMessageBuilder {
    val url = URLBuilder()
    var method = HttpMethod.Get
    override val headers = HeadersBuilder()
    var body: Any = EmptyContent

    val executionContext = CompletableDeferred<Unit>()

    fun headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

    fun url(block: URLBuilder.(URLBuilder) -> Unit) = url.block(url)

    fun build(): HttpRequestData = HttpRequestData(
            url.build(), method, headers.build(), body, executionContext
    )
}

class HttpRequestData(
        val url: Url,
        val method: HttpMethod,
        val headers: Headers,
        val body: Any,
        val executionContext: CompletableDeferred<Unit>
)

fun HttpRequestBuilder.takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
    method = builder.method
    body = builder.body
    url.takeFrom(builder.url)
    headers.appendAll(builder.headers)

    return this
}

fun HttpRequestBuilder.url(
        scheme: String = "http",
        host: String = "localhost",
        port: Int = 80,
        path: String = "/"
) {
    url.apply {
        protocol = URLProtocol.createOrDefault(scheme)
        this.host = host
        this.port = port
        encodedPath = path
    }
}

fun HttpRequestBuilder.url(data: java.net.URL) {
    url.takeFrom(data)
}