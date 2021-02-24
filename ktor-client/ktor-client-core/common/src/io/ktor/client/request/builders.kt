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
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    builder: HttpRequestBuilder = HttpRequestBuilder()
): T = request(builder).body()

/**
 * Executes a [HttpClient] request, with the information configured in [builder] block
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(block: HttpRequestBuilder.() -> Unit): T =
    request(HttpRequestBuilder().apply(block)).body()

/**
 * Executes a [HttpClient] request, with the [urlString] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(urlString) { block }.body<T>()", "io.ktor.client.call.body")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request(urlString, block).body()

/**
 * Executes a [HttpClient] request, with the [url] and the information configured in builder [block]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("request(url, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("requestAs")
public suspend inline fun <reified T> HttpClient.request(
    url: Url,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request(url, block).body()

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
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("get(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Get
    return request(builder).body()
}

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Post
    return request(builder).body()
}

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Put
    return request(builder).body()
}

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Delete
    return request(builder).body()
}

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Options
    return request(builder).body()
}

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Patch
    return request(builder).body()
}

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 * and tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(builder).body<T>()", "io.ktor.client.call.body")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(builder: HttpRequestBuilder): T {
    builder.method = HttpMethod.Head
    return request(builder).body()
}

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
    get(blockWithDefaults(block))

/**
 * Executes a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.post(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    post(blockWithDefaults(block))

/**
 * Executes a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.put(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    put(blockWithDefaults(block))

/**
 * Executes a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.delete(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    delete(blockWithDefaults(block))

/**
 * Executes a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.options(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    options(blockWithDefaults(block))

/**
 * Executes a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.patch(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    patch(blockWithDefaults(block))

/**
 * Executes a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.head(block: HttpRequestBuilder.() -> Unit): HttpResponse =
    head(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] GET request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareGet(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareGet(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] POST request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePost(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePost(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] PUT request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePut(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePut(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] DELETE request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareDelete(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareDelete(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] OPTIONS request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareOptions(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareOptions(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] PATCH request, with the information from the [builder]
 */
public suspend inline fun HttpClient.preparePatch(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    preparePatch(blockWithDefaults(block))

/**
 * Prepares a [HttpClient] HEAD request, with the information from the [builder]
 */
public suspend inline fun HttpClient.prepareHead(block: HttpRequestBuilder.() -> Unit): HttpStatement =
    prepareHead(blockWithDefaults(block))

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
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Get
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Post
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Put
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Delete
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Patch
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Head
    setBody(body)
    apply(block)
}.body()

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
    setBody(body)
    apply(block)
}.body<T>()", "io.ktor.client.call.body"""
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
): T = request {
    url(scheme, host, port, path)
    method = HttpMethod.Options
    setBody(body)
    apply(block)
}.body()

/**
 * Creates a [HttpRequestBuilder] and configures it with a [block] of code.
 */
public fun request(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply(block)

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("get(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("getAs")
public suspend inline fun <reified T> HttpClient.get(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("post(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("postAs")
public suspend inline fun <reified T> HttpClient.post(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] PUT request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("put(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("putAs")
public suspend inline fun <reified T> HttpClient.put(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = put {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] DELETE request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("delete(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("deleteAs")
public suspend inline fun <reified T> HttpClient.delete(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = delete {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] OPTIONS request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("options(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("optionsAs")
public suspend inline fun <reified T> HttpClient.options(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = options {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] PATCH request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("patch(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("patchAs")
public suspend inline fun <reified T> HttpClient.patch(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = patch {
    url.takeFrom(urlString)
    block()
}.body()

/**
 * Executes a [HttpClient] HEAD request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("head(urlString, block).body<T>()", "io.ktor.client.call.body")
)
@JvmName("headAs")
public suspend inline fun <reified T> HttpClient.head(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = head {
    url.takeFrom(urlString)
    block()
}.body()

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

@PublishedApi
internal inline fun blockWithDefaults(block: HttpRequestBuilder.() -> Unit): HttpRequestBuilder =
    HttpRequestBuilder().apply {
        url(scheme = "http", host = "localhost", port = DEFAULT_PORT, path = "/")
        block()
    }
