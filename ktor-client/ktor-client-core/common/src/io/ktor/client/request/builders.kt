/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlin.jvm.*

/**
 * Executes a [HttpClient] request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(builder).bodyAs<T>()")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): T = HttpStatement(builder, this).receive()

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(block).bodyAs<T>()")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
    request<T>(HttpRequestBuilder().apply(block))

/**
 * Executes a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(urlString, block).bodyAs<T>()")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T>(
    HttpRequestBuilder().apply {
        url(urlString)
        block()
    }
)

/**
 * Executes a [HttpClient] request, with the [url] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(url, block).bodyAs<T>()")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T>(
    HttpRequestBuilder().apply {
        url(url)
        block()
    }
)

/**
 * Executes a [HttpClient] request, with the information from the [builder]
 */
public suspend fun HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpResponse = request<HttpResponse>(builder)

/**
 * Prepares a [HttpClient] request, with the information from the [builder]
 */
public suspend fun HttpClient.prepareRequest(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): HttpStatement = request<HttpStatement>(builder)

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 */
public suspend fun HttpClient.request(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    request<HttpResponse>(block)

/**
 * Prepares a [HttpClient] request, with the information configured in [builder] block
 */
public suspend fun HttpClient.prepareRequest(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    request<HttpStatement>(block)

/**
 * Executes a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 */
public suspend fun HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request<HttpResponse>(urlString, block)

/**
 * Prepares a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 */
public suspend fun HttpClient.prepareRequest(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = request<HttpStatement>(urlString, block)

/**
 * Executes a [HttpClient] request, with the [url] and the information configured in builder [block]
 */
public suspend fun HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request<HttpResponse>(url, block)

/**
 * Prepares a [HttpClient] request, with the [url] and the information configured in builder [block]
 */
public suspend fun HttpClient.prepareRequest(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = request<HttpStatement>(url, block)

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("get(builder).bodyAs<T>()")
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(builder).bodyAs<T>()")
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Post
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(builder).bodyAs<T>()")
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Put
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(builder).bodyAs<T>()")
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Delete
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(builder).bodyAs<T>()")
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Options
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(builder).bodyAs<T>()")
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Patch
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(builder).bodyAs<T>()")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Head
    return request<T>(builder)
}

/**
 * Executes a [HttpClient] GET request, with the information from the [builder]
 */
public suspend fun HttpClient.get(builder: HttpRequestBuilder): HttpResponse = get<HttpResponse>(builder)

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 */
public suspend fun HttpClient.post(builder: HttpRequestBuilder): HttpResponse = post<HttpResponse>(builder)

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend fun HttpClient.put(builder: HttpRequestBuilder): HttpResponse = put<HttpResponse>(builder)

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend fun HttpClient.delete(builder: HttpRequestBuilder): HttpResponse = delete<HttpResponse>(builder)

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend fun HttpClient.options(builder: HttpRequestBuilder): HttpResponse = options<HttpResponse>(builder)

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend fun HttpClient.patch(builder: HttpRequestBuilder): HttpResponse = patch<HttpResponse>(builder)

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend fun HttpClient.head(builder: HttpRequestBuilder): HttpResponse = head<HttpResponse>(builder)

/**
 * Prepares a [HttpClient] GET request, with the information from the [builder]
 */
public suspend fun HttpClient.prepareGet(builder: HttpRequestBuilder): HttpStatement = get<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] POST request, with the information from the [builder]
 */
public suspend fun HttpClient.preparePost(builder: HttpRequestBuilder): HttpStatement = post<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend fun HttpClient.preparePut(builder: HttpRequestBuilder): HttpStatement = put<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend fun HttpClient.prepareDelete(builder: HttpRequestBuilder): HttpStatement = delete<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend fun HttpClient.prepareOptions(builder: HttpRequestBuilder): HttpStatement =
    options<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend fun HttpClient.preparePatch(builder: HttpRequestBuilder): HttpStatement = patch<HttpStatement>(builder)

/**
 * Prepares a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend fun HttpClient.prepareHead(builder: HttpRequestBuilder): HttpStatement = head<HttpStatement>(builder)

/**
 * Executes a [HttpClient] GET request, with the specified [scheme], [host], [port], [path] and [body].
 * And allows to further configure the request, using a [block] receiving an [HttpRequestBuilder].
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """get {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """post {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """put {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """delete {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """patch {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """head {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function with HttpRequestBuilder parameter",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith(
        """options {
    url(scheme, host, port, path)
    this.body = body
    apply(block)
}.bodyAs<T>()"""
    )
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    body: Any = EmptyContent,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("get(urlString, block).bodyAs<T>()")
)
@JvmName("getAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(urlString, block).bodyAs<T>()")
)
@JvmName("postAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(urlString, block).bodyAs<T>()")
)
@JvmName("putAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(urlString, block).bodyAs<T>()")
)
@JvmName("deleteAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(urlString, block).bodyAs<T>()")
)
@JvmName("optionsAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(urlString, block).bodyAs<T>()")
)
@JvmName("patchAs")
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
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(urlString, block).bodyAs<T>()")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head {
    url.takeFrom(urlString)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.get(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = get<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.post(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = post<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.put(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = put<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.delete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = delete<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.options(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = options<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.patch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = patch<HttpResponse>(urlString, block)

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = head<HttpResponse>(urlString, block)

/**
 * Prepares a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareGet(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = get<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePost(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = post<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePut(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = put<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareDelete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = delete<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareOptions(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = options<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.preparePatch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = patch<HttpStatement>(urlString, block)

/**
 * Prepares a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 */
public suspend fun HttpClient.prepareHead(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = head<HttpStatement>(urlString, block)
