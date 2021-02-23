/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request.forms

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlin.jvm.*

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("submitForm(formParameters, encodeInQuery, block).bodyAs<T>()")
)
@JvmName("submitFormAs")
public suspend inline fun <reified T> HttpClient.submitForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        body = FormDataContent(formParameters)
    }

    block()
}

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [url] destination
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("submitForm(url, formParameters, encodeInQuery, block).bodyAs<T>()")
)
@JvmName("submitFormAs")
public suspend inline fun <reified T> HttpClient.submitForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm<T>(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("submitFormWithBinaryData(formData, block).bodyAs<T>()")
)
@JvmName("submitFormWithBinaryDataAs")
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request<T> {
    method = HttpMethod.Post
    body = MultiPartFormDataContent(formData)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [url] destination
 * [formData] encoded using multipart/form-data format.
 *
 * https://tools.ietf.org/html/rfc2045
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith("submitFormWithBinaryData(formData, block).bodyAs<T>()")
)
@JvmName("submitFormWithBinaryDataAs")
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitFormWithBinaryData<T>(formData) {
    url(url)
    block()
}

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith(
        "submitForm(formParameters, encodeInQuery, block) " +
            "{ scheme = scheme; host = host; port = port; path = path }.bodyAs<T>()"
    )
)
@JvmName("submitFormWithBinaryDataAs")
public suspend inline fun <reified T> HttpClient.submitForm(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    path: String = "/",
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm<T>(formParameters, encodeInQuery) {
    url(scheme, host, port, path)
    apply(block)
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
@Deprecated(
    "Please use function without generic argument",
    replaceWith = ReplaceWith(
        "submitForm(formParameters, block) " +
            "{ scheme = scheme; host = host; port = port; path = path }.bodyAs<T>()"
    )
)
@JvmName("submitFormWithBinaryDataAs")
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    path: String = "/",
    formData: List<PartData> = emptyList(),
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitFormWithBinaryData<T>(formData) {
    url(scheme, host, port, path)
    apply(block)
}

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
public suspend inline fun HttpClient.submitForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request<HttpResponse> {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        body = FormDataContent(formParameters)
    }

    block()
}

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [url] destination
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
public suspend fun HttpClient.submitForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = submitForm(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun HttpClient.submitFormWithBinaryData(
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request<HttpResponse> {
    method = HttpMethod.Post
    body = MultiPartFormDataContent(formData)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [url] destination
 * [formData] encoded using multipart/form-data format.
 *
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun HttpClient.submitFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = submitFormWithBinaryData(formData) {
    url(url)
    block()
}

/**
 * Prepare [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
public suspend inline fun HttpClient.prepareForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = request<HttpStatement> {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        body = FormDataContent(formParameters)
    }

    block()
}

/**
 * Prepare [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [url] destination
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
public suspend fun HttpClient.prepareForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareForm(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Prepare [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun HttpClient.prepareFormWithBinaryData(
    formData: List<PartData>,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareRequest {
    method = HttpMethod.Post
    body = MultiPartFormDataContent(formData)
    block()
}

/**
 * Prepare [HttpMethod.Post] request with [formData] encoded in body.
 * [url] destination
 * [formData] encoded using multipart/form-data format.
 *
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun HttpClient.prepareFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): HttpStatement = prepareFormWithBinaryData(formData) {
    url(url)
    block()
}
