/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request.forms

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
public suspend inline fun HttpClient.submitForm(
    formParameters: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse = request {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        setBody(FormDataContent(formParameters))
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
): HttpResponse = request {
    method = HttpMethod.Post
    setBody(MultiPartFormDataContent(formData))
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
): HttpStatement = prepareRequest {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formParameters)
    } else {
        method = HttpMethod.Post
        setBody(FormDataContent(formParameters))
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
    setBody(MultiPartFormDataContent(formData))
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
