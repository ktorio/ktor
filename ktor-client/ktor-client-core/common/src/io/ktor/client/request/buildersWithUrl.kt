/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.jvm.*

/**
 * Executes a [HttpClient] GET request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.get(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareGet(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = get<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] POST request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.post(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.praparePost(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = post<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.put(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePut(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = put<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.patch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePatch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = patch<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.options(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareOptions(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = options<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.head(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareHead(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = head<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
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
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.delete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareDelete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = delete<HttpStatement>(url, block)

/**
 * Sets the [HttpRequestBuilder.url] from [url].
 */
public fun HttpRequestBuilder.url(url: Url): Unit { // ktlint-disable no-unit-return
    this.url.takeFrom(url)
}
