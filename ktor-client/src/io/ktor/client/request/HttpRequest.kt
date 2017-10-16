package io.ktor.client.request

import io.ktor.client.utils.*
import io.ktor.http.HttpMethod
import io.ktor.util.Attributes
import java.nio.charset.Charset


class HttpRequest(val url: Url, val method: HttpMethod, val headers: Headers, val payload: Any) {
    val cacheControl: HttpRequestCacheControl by lazy { headers.computeRequestCacheControl() }
}

class HttpRequestBuilder() {
    constructor(data: HttpRequest) : this() {
        method = data.method
        url.takeFrom(data.url)
        headers.appendAll(data.headers)
    }

    var method = HttpMethod.Get
    val url = UrlBuilder()
    val headers = HeadersBuilder()
    var payload: Any = Unit
    var charset: Charset? = null

    val flags = Attributes()

    val cacheControl: HttpRequestCacheControl get() = headers.computeRequestCacheControl()

    fun headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

    fun url(block: UrlBuilder.() -> Unit) = url.block()

    fun build(): HttpRequest = HttpRequest(url.build(), method, valuesOf(headers), payload)
}

