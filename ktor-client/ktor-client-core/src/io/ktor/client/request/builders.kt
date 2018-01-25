package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.net.*


suspend inline fun <reified T> HttpClient.request(
        builder: HttpRequestBuilder = HttpRequestBuilder()
): T = call(builder).receive()

suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
        request(HttpRequestBuilder().apply(block))

suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

suspend inline fun <reified T> HttpClient.post(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

suspend inline fun <reified T> HttpClient.get(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "/",
        body: Any = EmptyContent,
        block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Get
    this.body = body
    apply(block)
}

suspend inline fun <reified T> HttpClient.post(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "/",
        body: Any = EmptyContent,
        block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Post
    this.body = body
    apply(block)
}

suspend inline fun <reified T> HttpClient.get(
        data: URL,
        block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    url.takeFrom(data)
    block()
}

suspend inline fun <reified T> HttpClient.post(
        data: URL,
        block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    url.takeFrom(data)
}

suspend inline fun <reified T> HttpClient.get(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {}
): T = get(URL(url), block = block)

suspend inline fun <reified T> HttpClient.post(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {}
): T = post(URL(url), block = block)

fun request(block: HttpRequestBuilder.() -> Unit) = HttpRequestBuilder().apply(block)
