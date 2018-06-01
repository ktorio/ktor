package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*

/**
 * Executes a [HttpClient] request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): T = call(builder).receive()

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
    request(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.post(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Post
    return request(builder)
}

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
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

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
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

/**
 * Creates a [HttpRequestBuilder] and configures it with a [block] of code.
 */
fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder = HttpRequestBuilder().apply(block)
