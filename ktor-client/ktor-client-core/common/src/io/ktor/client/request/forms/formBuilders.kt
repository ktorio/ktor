/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request.forms

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Submit [formParameters] request.
 *
 * If [encodeInQuery] specified encode [formParameters] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formParameters] encoded in body.
 *
 * [formParameters] encoded using application/x-www-form-urlencoded format.
 */
public suspend inline fun <reified T> HttpClient.submitForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
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
public suspend inline fun <reified T> HttpClient.submitForm(
    url: String,
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm(formParameters, encodeInQuery) {
    url(url)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
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
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitFormWithBinaryData(formData) {
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
public suspend inline fun <reified T> HttpClient.submitForm(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    path: String = "/",
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm(formParameters, encodeInQuery) {
    url(scheme, host, port, path)
    apply(block)
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
public suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    path: String = "/",
    formData: List<PartData> = emptyList(),
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitFormWithBinaryData(formData) {
    url(scheme, host, port, path)
    apply(block)
}
