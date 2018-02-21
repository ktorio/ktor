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
    var method: HttpMethod = HttpMethod.Get
    override val headers = HeadersBuilder()
    var body: Any = EmptyContent

    val executionContext: CompletableDeferred<Unit> = CompletableDeferred<Unit>()

    fun build(): HttpRequestData = HttpRequestData(
            url.build(), method, headers.build(), body, executionContext
    )

    companion object
}

class HttpRequestData(
        val url: Url,
        val method: HttpMethod,
        val headers: Headers,
        val body: Any,
        val executionContext: CompletableDeferred<Unit>
)

fun HttpRequestBuilder.headers(block: HeadersBuilder.() -> Unit): HeadersBuilder = headers.apply(block)

fun HttpRequestBuilder.takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
    method = builder.method
    body = builder.body
    url.takeFrom(builder.url)
    headers.appendAll(builder.headers)

    return this
}

fun HttpRequestBuilder.url(block: URLBuilder.() -> Unit): Unit = block(url)

operator fun HttpRequestBuilder.Companion.invoke(block: URLBuilder.() -> Unit): HttpRequestBuilder =
        HttpRequestBuilder().apply { url(block) }

fun HttpRequestBuilder.url(
        scheme: String = "http", host: String = "localhost", port: Int = 80, path: String = "/",
        block: URLBuilder.() -> Unit = {}
): Unit {
    url.apply {
        protocol = URLProtocol.createOrDefault(scheme)
        this.host = host
        this.port = port
        encodedPath = path
        block(url)
    }
}

operator fun HttpRequestBuilder.Companion.invoke(
        scheme: String = "http", host: String = "localhost", port: Int = 80, path: String = "/",
        block: URLBuilder.() -> Unit = {}
): HttpRequestBuilder = HttpRequestBuilder().apply { url(scheme, host, port, path, block) }

fun HttpRequestBuilder.url(url: java.net.URL): Unit = this.url.takeFrom(url)

operator fun HttpRequestBuilder.Companion.invoke(url: java.net.URL): HttpRequestBuilder =
        HttpRequestBuilder().apply { url(url) }
