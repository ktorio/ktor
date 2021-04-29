/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*

/**
 * Executes a [HttpClient] request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): T = HttpStatement(builder, this).receive()

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
    request(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request(
    HttpRequestBuilder().apply {
        url(urlString)
        block()
    }
)

/**
 * Executes a [HttpClient] request, with the [url] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request(
    HttpRequestBuilder().apply {
        url(url)
        block()
    }
)

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder)
}

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Post
    return request(builder)
}

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Put
    return request(builder)
}

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Delete
    return request(builder)
}

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Options
    return request(builder)
}

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Patch
    return request(builder)
}

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Head
    return request(builder)
}

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
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
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
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
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Put
    this.body = body
    apply(block)
}

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Delete
    this.body = body
    apply(block)
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Patch
    this.body = body
    apply(block)
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Head
    this.body = body
    apply(block)
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Options
    this.body = body
    apply(block)
}

/**
 * Creates a [HttpRequestBuilder] and configures it with a [block] of code.
 */
public fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder = HttpRequestBuilder().apply(block)

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head {
    url.takeFrom(urlString)
    block()
}
