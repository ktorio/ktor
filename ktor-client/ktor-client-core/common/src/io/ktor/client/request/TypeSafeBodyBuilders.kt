/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*

/**
 * Set [body] for this request. Initially [EmptyContent].
 */
public inline fun <reified T : Any> HttpRequestBuilder.setBody(body: T) {
    when (body) {
        is TypedBody<*> -> {
            this.body = body.body
            this.bodyType = body.type
        }
        is String,
        is OutgoingContent,
        is ByteArray,
        is ByteReadChannel -> {
            this.body = body
        }
        else -> {
            this.body = body
            bodyType = tryGetType(body)
        }
    }
}

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Delete
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Delete
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Delete
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Options
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Put
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] POST request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Post
    this.body = body.body
    bodyType = body.type
    apply(block)
}

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: OutgoingContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteArray,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: ByteReadChannel,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(scheme, host, port, path, body as Any, block)

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 *
 * Should be used when body has type that doesn't have corresponding overload. Wrap your body object in [TypedBody] with [bodyOf] function
 */
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http", host: String = "localhost", port: Int = DEFAULT_PORT,
    path: String = "/",
    body: TypedBody<*>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Get
    this.body = body.body
    bodyType = body.type
    apply(block)
}
