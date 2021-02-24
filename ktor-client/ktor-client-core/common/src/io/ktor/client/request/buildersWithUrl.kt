/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
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
    replaceWith = ReplaceWith("get(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(url, block).body()

/**
 * Executes a [HttpClient] GET request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.get(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareGet(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareGet {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(url, block).body()

/**
 * Executes a [HttpClient] POST request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.post(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePost(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePost {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put(url, block).body()

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.put(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePut(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePut {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch(url, block).body()

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.patch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePatch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePatch {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options(url, block).body()

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.options(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareOptions(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareOptions {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head(url, block).body()

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.head(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareHead(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareHead {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete(url, block).body()

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.delete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as Url and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareDelete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareDelete {
    this.url.takeFrom(url)
    block()
}

/**
 * Sets the [HttpRequestBuilder.url] from [url].
 */
public fun HttpRequestBuilder.url(url: Url): Unit { // ktlint-disable no-unit-return
    this.url.takeFrom(url)
}
