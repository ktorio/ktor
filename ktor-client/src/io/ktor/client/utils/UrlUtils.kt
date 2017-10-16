package io.ktor.client.utils

import io.ktor.client.request.HttpRequestBuilder
import java.net.URL


fun HttpRequestBuilder.url(
        scheme: String = "http",
        host: String = "localhost",
        port: Int = 80,
        path: String = ""
) {
    url.apply {
        this.scheme = scheme
        this.host = host
        this.port = port
        this.path = path
    }
}

fun HttpRequestBuilder.url(data: Url) {
    url.takeFrom(data)
}

fun UrlBuilder.takeFrom(url: Url): UrlBuilder {
    scheme = url.scheme
    host = url.host
    port = url.port
    path = url.path
    username = url.username
    password = url.password
    queryParameters = ParametersBuilder().apply {
        appendAll(url.queryParameters)
    }

    return this
}

fun UrlBuilder.takeFrom(data: URL) {
    scheme = data.protocol
    host = data.host
    path = data.path
    port = data.port.takeIf { it > 0 } ?: if (scheme == "https") 443 else 80

    // TODO: parse query parameters
}

fun UrlBuilder.takeFrom(url: String) = takeFrom(URL(url))
