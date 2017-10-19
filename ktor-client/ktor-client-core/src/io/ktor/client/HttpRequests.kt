package io.ktor.client

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.net.*


suspend inline fun <reified T> HttpClient.request(builder: HttpRequestBuilder = HttpRequestBuilder()): T =
        call(builder).receive()

suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
        request(HttpRequestBuilder().apply(block))

suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

suspend inline fun <reified T> HttpClient.get(
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

suspend inline fun <reified T> HttpClient.get(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit
): T = get(scheme, host, port, path, payload, {})

suspend inline fun <reified T> HttpClient.get(data: URL): T = get {
    url.takeFrom(data)
}

suspend inline fun <reified T> HttpClient.get(url: String): T = get(URL(decodeURLPart(url)))

suspend inline fun <reified T> HttpClient.post(
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

suspend inline fun <reified T> HttpClient.post(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "",
        payload: Any = Unit
): T = post(scheme, host, port, path, payload, {})

suspend inline fun <reified T> HttpClient.post(data: URL): T = post {
    url.takeFrom(data)
}

suspend inline fun <reified T> HttpClient.post(url: String): T = post(URL(decodeURLPart(url)))

fun request(block: HttpRequestBuilder.() -> Unit) = HttpRequestBuilder().apply(block)