/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.*

/**
 * Executes a [HttpClient] request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(url, block).bodyAs<T>()")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("get(url, block).bodyAs<T>()")
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(url, block).bodyAs<T>()")
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(url, block).bodyAs<T>()")
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(url, block).bodyAs<T>()")
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(url, block).bodyAs<T>()")
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(url, block).bodyAs<T>()")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(url, block).bodyAs<T>()")
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.request(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend fun HttpClient.get(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.post(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.put(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.patch(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.options(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.head(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.delete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete<HttpResponse> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareRequest(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = request<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend fun HttpClient.prepareGet(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = get<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePost(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = post<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePut(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = put<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePatch(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = patch<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePptions(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = options<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareHead(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = head<HttpStatement> {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareDelete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = delete<HttpStatement> {
    this.url.takeFrom(url)
    block()
}
