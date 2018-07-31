package io.ktor.client.request

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.io.core.*

/**
 * Encode [formData] for the post request using application/x-www-form-urlencoded format.
 */
class FormDataContent(
    val formData: Parameters
) : OutgoingContent.ByteArrayContent() {
    private val content = buildPacket { writeStringUtf8(formData.formUrlEncode()) }.readBytes()

    override val contentLength: Long = content.size.toLong()
    override val contentType: ContentType = ContentType.Application.FormUrlEncoded

    override fun bytes(): ByteArray = content
}

/**
 * Submit [formData] request.
 *
 * If [urlEncoded] specified encode [formData] in url parameters and use [HttpMethod.Get] for the request.
 * Otherwise send [HttpMethod.Post] request with [formData] encoded in body.
 *
 * [formData] encoded using application/x-www-form-urlencoded format.
 */
suspend inline fun <reified T> HttpClient.submitForm(
    formData: Parameters = Parameters.Empty,
    urlEncoded: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = request {
    if (urlEncoded) {
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
 * If [urlEncoded] specified encode [formData] in url parameters and use [HttpMethod.Get] for the request.
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
    urlEncoded: Boolean = false,
    block: HttpRequestBuilder.() -> Unit = {}
): T = submitForm(formData, urlEncoded) {
    url(scheme, host, port, path)
    apply(block)
}

