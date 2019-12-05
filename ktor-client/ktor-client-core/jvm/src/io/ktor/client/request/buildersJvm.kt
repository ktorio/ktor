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
suspend inline fun <reified T> HttpClient.request(
    url: URL, block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.get(
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
suspend inline fun <reified T> HttpClient.post(
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
suspend inline fun <reified T> HttpClient.put(
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
suspend inline fun <reified T> HttpClient.patch(
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
suspend inline fun <reified T> HttpClient.options(
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
suspend inline fun <reified T> HttpClient.head(
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
suspend inline fun <reified T> HttpClient.delete(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete {
    this.url.takeFrom(url)
    block()
}
