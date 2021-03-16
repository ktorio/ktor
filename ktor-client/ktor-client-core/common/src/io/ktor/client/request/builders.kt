/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.jvm.*

/**
 * Executes a [HttpClient] request, with the information from the [builder]
 */
public suspend inline fun HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpResponse = HttpStatement(builder, this).receive()

/**
 * Prepares a [HttpClient] request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareRequest(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpStatement = HttpStatement(builder, this)

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 */
public suspend inline fun HttpClient.request(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    request(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] request, with the information configured in [builder] block
 */
public suspend inline fun HttpClient.prepareRequest(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareRequest(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 */
public suspend inline fun HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request(
    HttpRequestBuilder().apply {
        url(urlString)
        block()
    }
)

/**
 * Prepares a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 */
public suspend inline fun HttpClient.prepareRequest(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest(
    HttpRequestBuilder().apply {
        url(urlString)
        block()
    }
)

/**
 * Executes a [HttpClient] request, with the [url] and the information configured in builder [block]
 */
public suspend inline fun HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request(
    HttpRequestBuilder().apply {
        url(url)
        block()
    }
)

/**
 * Prepares a [HttpClient] request, with the [url] and the information configured in builder [block]
 */
public suspend inline fun HttpClient.prepareRequest(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest(
    HttpRequestBuilder().apply {
        url(url)
        block()
    }
)

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 */
public suspend inline fun HttpClient.get(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Get
    return request(builder)
}

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.post(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Post
    return request(builder)
}

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.put(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Put
    return request(builder)
}

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.delete(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Delete
    return request(builder)
}

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.options(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Options
    return request(builder)
}

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.patch(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Patch
    return request(builder)
}

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.head(builder: HttpRequestBuilder): HttpResponse {
    builder.method = HttpMethod.Head
    return request(builder)
}

/**
 * Prepares a [HttpClient] GET request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareGet(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Get
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePost(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Post
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePut(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Put
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareDelete(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Delete
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareOptions(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Options
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePatch(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Patch
    return prepareRequest(builder)
}

/**
 * Prepares a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareHead(builder: HttpRequestBuilder): HttpStatement {
    builder.method = HttpMethod.Head
    return prepareRequest(builder)
}

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 */
public suspend inline fun HttpClient.get(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    get(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.post(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    post(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.put(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    put(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.delete(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    delete(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.options(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    options(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.patch(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    patch(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.head(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    head(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] GET request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareGet(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareGet(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePost(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePost(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePut(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePut(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareDelete(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareDelete(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareOptions(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareOptions(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePatch(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePatch(HttpRequestBuilder().apply(block))

/**
 * Prepares a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareHead(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareHead(HttpRequestBuilder().apply(block))

/**
 * Creates a [HttpRequestBuilder] and configures it with a [block] of code.
 */
public fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply(block)

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.get(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get(Url(urlString), block)

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.post(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post(Url(urlString), block)

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.put(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put(Url(urlString), block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.delete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete(Url(urlString), block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.options(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options(Url(urlString), block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.patch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch(Url(urlString), block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head(Url(urlString), block)

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareGet(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareGet(Url(urlString), block)

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePost(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePost(Url(urlString), block)

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePut(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePut(Url(urlString), block)

/**
 * Prepares a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareDelete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareDelete(Url(urlString), block)

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareOptions(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareOptions(Url(urlString), block)

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.preparePatch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = preparePatch(Url(urlString), block)

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend inline fun HttpClient.prepareHead(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareHead(Url(urlString), block)
