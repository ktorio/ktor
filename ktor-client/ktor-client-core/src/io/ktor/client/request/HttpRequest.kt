package io.ktor.client.request

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.nio.charset.*
import javax.net.ssl.*


class HttpRequest(
        val url: Url,
        val method: HttpMethod,
        val headers: Headers,
        val body: Any,
        val charset: Charset?, // why not content type?
        var sslContext: SSLContext?,
        val followRedirects: Boolean // should it be here?
) {
    val cacheControl: HttpRequestCacheControl by lazy { headers.computeRequestCacheControl() } // and this?
}

class HttpRequestBuilder() {
    val url = UrlBuilder()
    var method = HttpMethod.Get
    val headers = HeadersBuilder(caseInsensitiveKey = true)
    var body: Any = EmptyBody
    var charset: Charset? = null
    var sslContext: SSLContext? = null
    var followRedirects: Boolean = false

    val flags = Attributes()
    val cacheControl: HttpRequestCacheControl get() = headers.computeRequestCacheControl()

    constructor(data: HttpRequest) : this() {
        url.takeFrom(data.url)
        method = data.method
        headers.appendAll(data.headers)
        body = data.body
        charset = data.charset
        sslContext = data.sslContext
        followRedirects = data.followRedirects
    }

    fun headers(block: HeadersBuilder.() -> Unit) = headers.apply(block)

    fun url(block: UrlBuilder.() -> Unit) = url.block()

    fun build(): HttpRequest = HttpRequest(url.build(), method, headers.build(), body, charset, sslContext, followRedirects)
}

fun HttpRequestBuilder.takeFrom(builder: HttpRequestBuilder): HttpRequestBuilder {
    method = builder.method
    body = builder.body
    charset = builder.charset
    sslContext = builder.sslContext
    followRedirects = builder.followRedirects
    url.takeFrom(builder.url)
    headers.appendAll(builder.headers)

    return this
}
