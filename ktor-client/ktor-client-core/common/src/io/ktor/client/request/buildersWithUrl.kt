/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Executes a [HttpClient] GET request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.get)
 */
public suspend inline fun HttpClient.get(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] GET request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.prepareGet)
 */
public suspend inline fun HttpClient.prepareGet(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareGet {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.post)
 */
public suspend inline fun HttpClient.post(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] POST request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.preparePost)
 */
public suspend inline fun HttpClient.preparePost(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePost {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PUT request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.put)
 */
public suspend inline fun HttpClient.put(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PUT request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.preparePut)
 */
public suspend inline fun HttpClient.preparePut(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePut {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] PATCH request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.patch)
 */
public suspend inline fun HttpClient.patch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] PATCH request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.preparePatch)
 */
public suspend inline fun HttpClient.preparePatch(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePatch {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] OPTIONS request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.options)
 */
public suspend inline fun HttpClient.options(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] OPTIONS request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.prepareOptions)
 */
public suspend inline fun HttpClient.prepareOptions(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareOptions {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.head)
 */
public suspend inline fun HttpClient.head(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.prepareHead)
 */
public suspend inline fun HttpClient.prepareHead(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareHead {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * Learn more from [Making requests](https://ktor.io/docs/request.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.delete)
 */
public suspend inline fun HttpClient.delete(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete {
    this.url.takeFrom(url)
    block()
}

/**
 * Prepares a [HttpClient] HEAD request with the specified [url] and
 * an optional [block] receiving an [HttpRequestBuilder] for configuring the request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.prepareDelete)
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.url)
 */
public fun HttpRequestBuilder.url(url: Url) {
    this.url.takeFrom(url)
}
