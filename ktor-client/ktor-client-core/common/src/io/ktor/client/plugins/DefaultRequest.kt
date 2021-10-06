/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Sets default request parameters. Used to add common headers and URL for a request.
 * Note that trailing slash in base URL and leading slash in request URL are important.
 * The rules to calculate a final URL:
 * 1. Request URL doesn't start with slash
 *     * Base URL ends with slash ->
 *       concat strings.
 *       Example:
 *       base = `https://example.com/dir/`,
 *       request = `file.html`,
 *       result = `https://example.com/dir/file.html`
 *     * Base URL doesn't end with slash ->
 *       remove last path segment of base URL and concat strings.
 *       Example:
 *       base = `https://example.com/dir/deafult_file.html`,
 *       request = `file.html`,
 *       result = `https://example.com/dir/file.html`
 * 2. Request URL starts with slash -> use request path as is.
 *   Example:
 *   base = `https://example.com/dir/deafult_file.html`,
 *   request = `/root/file.html`,
 *   result = `https://example.com/root/file.html`
 *
 * Usage:
 * ```
 * val client = HttpClient {
 *   defaultRequest {
 *     url("https://base.url/dir/")
 *     headers.append(HttpHeaders.ContentType, ContentType.Application.Json)
 *   }
 * }
 * client.get("file") // <- requests "https://base.url/dir/file"
 * client.get("/other_root/file") // <- requests "https://base.url/other_root/file"
 * client.get("//other.host/path") // <- requests "https://other.host/path"
 * ```
 */
public class DefaultRequest private constructor(private val block: HttpRequestBuilder.() -> Unit) {

    public companion object Plugin : HttpClientPlugin<HttpRequestBuilder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: HttpRequestBuilder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(plugin: DefaultRequest, scope: HttpClient) {
            val defaultRequest = HttpRequestBuilder().apply(plugin.block).build()
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                if (context.url.host.isEmpty()) {
                    mergeUrls(defaultRequest.url, context.url)
                }
                context.headers.appendMissing(defaultRequest.headers)
            }
        }

        private fun mergeUrls(baseUrl: Url, requestUrl: URLBuilder) {
            val url = URLBuilder(baseUrl)
            with(requestUrl) {
                if (encodedPathSegments.size > 1 && encodedPathSegments.first().isEmpty()) {
                    // path starts from "/"
                    url.encodedPathSegments = encodedPathSegments
                } else if (encodedPathSegments.size != 1 || encodedPathSegments.first().isNotEmpty()) {
                    url.encodedPathSegments = url.encodedPathSegments.dropLast(1) + encodedPathSegments
                }
                url.encodedFragment = encodedFragment
                url.encodedParameters = encodedParameters
                takeFrom(url)
            }
        }
    }
}

/**
 * Set default request parameters. See [DefaultRequest]
 */
public fun HttpClientConfig<*>.defaultRequest(block: HttpRequestBuilder.() -> Unit) {
    install(DefaultRequest) {
        block()
    }
}
