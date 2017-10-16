package io.ktor.client

import io.ktor.client.call.call
import io.ktor.client.call.receive
import io.ktor.client.pipeline.HttpClientScope
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.utils.takeFrom
import io.ktor.client.utils.url
import io.ktor.http.HttpMethod
import java.net.URL


suspend inline fun <reified T> HttpClientScope.request(builder: HttpRequestBuilder = HttpRequestBuilder()): T =
        call(builder).receive<T>()

suspend inline fun <reified T> HttpClientScope.request(block: HttpRequestBuilder.() -> Unit): T =
        request(HttpRequestBuilder().apply(block))

suspend inline fun <reified T> HttpClientScope.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

suspend inline fun <reified T> HttpClientScope.get(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit,
        block: HttpRequestBuilder.() -> Unit
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Get
    this.payload = payload
    apply(block)
}

suspend inline fun <reified T> HttpClientScope.get(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit
): T = get(scheme, host, port, path, payload, {})

suspend inline fun <reified T> HttpClientScope.get(data: URL): T = get {
    url.takeFrom(data)
}

suspend inline fun <reified T> HttpClientScope.get(url: String): T = get(URL(url))

suspend inline fun <reified T> HttpClientScope.post(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit,
        block: HttpRequestBuilder.() -> Unit
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Post
    this.payload = payload
    apply(block)
}

suspend inline fun <reified T> HttpClientScope.post(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit
): T = post(scheme, host, port, path, payload, {})

fun request(block: HttpRequestBuilder.() -> Unit) = HttpRequestBuilder().apply(block)