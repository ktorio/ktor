package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A request for [HttpClient], first part of [HttpClientCall].
 */
interface HttpRequest : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    val call: HttpClientCall

    override val coroutineContext: CoroutineContext get() = call.coroutineContext

    /**
     * The [HttpMethod] or HTTP VERB used for this request.
     */
    val method: HttpMethod

    /**
     * The [Url] representing the endpoint and the uri for this request.
     */
    val url: Url

    /**
     * Typed [Attributes] associated to this call serving as a lightweight container.
     */
    val attributes: Attributes

    /**
     * A [Job] representing the process of this request.
     */
    @Deprecated(
        "executionContext is deprecated. Use coroutineContext instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("coroutineContext")
    )
    val executionContext: Job
        get() = TODO()

    /**
     * An [OutgoingContent] representing the request body
     */
    val content: OutgoingContent
}

/**
 * Default [HttpRequest] implementation.
 */
open class DefaultHttpRequest(override val call: HttpClientCall, data: HttpRequestData) : HttpRequest {
    override val coroutineContext: CoroutineContext get() = call.coroutineContext

    override val method: HttpMethod = data.method

    override val url: Url = data.url

    override val content: OutgoingContent = data.body as OutgoingContent

    override val headers: Headers = data.headers

    override val attributes: Attributes = data.attributes
}

/**
 * Class for building [HttpRequestData].
 */
class HttpRequestBuilder : HttpMessageBuilder {
    /**
     * [URLBuilder] to configure the URL for this request.
     */
    val url: URLBuilder = URLBuilder()

    /**
     * [HttpMethod] used by this request. [HttpMethod.Get] by default.
     */
    var method: HttpMethod = HttpMethod.Get

    /**
     * [HeadersBuilder] to configure the headers for this request.
     */
    override val headers: HeadersBuilder = HeadersBuilder()

    /**
     * The [body] for this request. Initially [EmptyContent].
     */
    var body: Any = EmptyContent

    /**
     * A deferred used to control the execution of this request.
     */
    @KtorExperimentalAPI
    val executionContext: Job = CompletableDeferred<Unit>()

    private var attributesBuilder: Attributes.() -> Unit = {}

    fun url(block: URLBuilder.(URLBuilder) -> Unit): Unit = url.block(url)

    /**
     * Create immutable [HttpRequestData]
     */
    fun build(): HttpRequestData = HttpRequestData(
        url.build(), method, headers.build(), body, executionContext, Attributes().apply(attributesBuilder)
    )

    /**
     * Set request specific attributes specified by [block]
     */
    fun setAttributes(block: Attributes.() -> Unit) {
        val old = attributesBuilder
        attributesBuilder = { old(); block() }
    }

    /**
     * Mutates [this] copying all the data from another [builder] using it as base.
     */
    fun takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
        method = builder.method
        body = builder.body
        url.takeFrom(builder.url)
        headers.appendAll(builder.headers)
        attributesBuilder = builder.attributesBuilder

        return this
    }

    companion object
}

/**
 * Actual data of the [HttpRequest], including [url], [method], [headers], [body] and [executionContext].
 * Built by [HttpRequestBuilder].
 */
class HttpRequestData internal constructor(
    val url: Url,
    val method: HttpMethod,
    val headers: Headers,
    val body: Any,
    val executionContext: Job,
    val attributes: Attributes
)

/**
 * Executes a [block] that configures the [HeadersBuilder] associated to this request.
 */
fun HttpRequestBuilder.headers(block: HeadersBuilder.() -> Unit): HeadersBuilder = headers.apply(block)


/**
 * Mutates [this] copying all the data from another [request] using it as base.
 */
fun HttpRequestBuilder.takeFrom(request: HttpRequest): HttpRequestBuilder {
    method = request.method
    body = request.content
    url.takeFrom(request.url)
    headers.appendAll(request.headers)

    return this
}

/**
 * Executes a [block] that configures the [URLBuilder] associated to this request.
 */
fun HttpRequestBuilder.url(block: URLBuilder.() -> Unit): Unit = block(url)

/**
 * Sets the [HttpRequestBuilder] from [request].
 */
fun HttpRequestBuilder.takeFrom(request: HttpRequestData): HttpRequestBuilder {
    method = request.method
    body = request.body
    url.takeFrom(request.url)
    headers.appendAll(request.headers)

    return this
}

/**
 * Executes a [block] that configures the [URLBuilder] associated to thisrequest.
 */
operator fun HttpRequestBuilder.Companion.invoke(block: URLBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply { url(block) }

/**
 * Sets the [url] using the specified [scheme], [host], [port] and [path].
 */
fun HttpRequestBuilder.url(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
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

/**
 * Constructs a [HttpRequestBuilder] from URL information: [scheme], [host], [port] and [path]
 * and optionally further configures it using [block].
 */
operator fun HttpRequestBuilder.Companion.invoke(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    block: URLBuilder.() -> Unit = {}
): HttpRequestBuilder = HttpRequestBuilder().apply { url(scheme, host, port, path, block) }

/**
 * Sets the [HttpRequestBuilder.url] from [urlString].
 */
fun HttpRequestBuilder.url(urlString: String): Unit {
    url.takeFrom(urlString)
}
