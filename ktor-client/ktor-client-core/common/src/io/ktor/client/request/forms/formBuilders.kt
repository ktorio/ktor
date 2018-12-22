package io.ktor.client.request.forms

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*


/**
 * Submit [formData] request.
 *
 * If [encodeInQuery] specified encode [formData] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formData] encoded in body.
 *
 * [formData] encoded using application/x-www-form-urlencoded format.
 */
suspend inline fun <reified T> HttpClient.submitForm(
    formData: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    if (encodeInQuery) {
        method = HttpMethod.Get
        url.parameters.appendAll(formData)
    } else {
        method = HttpMethod.Post
        body = FormDataContent(formData)
    }

    block()
}

/**
 * Submit [formData] request.
 *
 * If [encodeInQuery] specified encode [formData] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formData] encoded in body.
 *
 * [url] destination
 * [formData] encoded using application/x-www-form-urlencoded format.
 */
suspend inline fun <reified T> HttpClient.submitForm(
    url: String,
    formData: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm(formData, encodeInQuery) {
    url(url)
    block()
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
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
suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
    url: String,
    formData: List<PartData>,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitFormWithBinaryData(formData) {
    url(url)
    block()
}


/**
 * Submit [formData] request.
 *
 * If [encodeInQuery] specified encode [formData] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formData] encoded in body.
 *
 * [formData] encoded using application/x-www-form-urlencoded format.
 */
suspend inline fun <reified T> HttpClient.submitForm(
    scheme: String = "http",
    host: String = "localhost",
    port: Int = 80,
    path: String = "/",
    formData: Parameters = Parameters.Empty,
    encodeInQuery: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm(formData, encodeInQuery) {
    url(scheme, host, port, path)
    apply(block)
}

/**
 * Send [HttpMethod.Post] request with [formData] encoded in body.
 * [formData] encoded using multipart/form-data format.
 * https://tools.ietf.org/html/rfc2045
 */
suspend inline fun <reified T> HttpClient.submitFormWithBinaryData(
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
