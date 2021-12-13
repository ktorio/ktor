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
 * Headers of the builder will be pre-populated with request headers.
 * You can use [HeadersBuilder.contains], [HeadersBuilder.appendIfNameMissing]
 * and [HeadersBuilder.appendIfNameAndValueMissing] to avoid appending some header twice.
 *
 * Usage:
 * ```
 * val client = HttpClient {
 *   defaultRequest {
 *     url("https://base.url/dir/")
 *     headers.appendIfNameMissing(HttpHeaders.ContentType, ContentType.Application.Json)
 *   }
 * }
 * client.get("file")
 *   // <- requests "https://base.url/dir/file", ContentType = Application.Json
 * client.get("/other_root/file")
 *   // <- requests "https://base.url/other_root/file", ContentType = Application.Json
 * client.get("//other.host/path")
 *   // <- requests "https://other.host/path", ContentType = Application.Json
 * client.get("https://some.url") { HttpHeaders.ContentType = ContentType.Application.Xml }
 *   // <- requests "https://some.url/", ContentType = Application.Xml
 * ```
 */
public class DefaultRequest private constructor(private val block: DefaultRequestBuilder.() -> Unit) {

    public companion object Plugin : HttpClientPlugin<DefaultRequestBuilder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: DefaultRequestBuilder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(plugin: DefaultRequest, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val defaultRequest = DefaultRequestBuilder().apply {
                    headers.appendAll(context.headers)
                    plugin.block(this)
                }
                val defaultUrl = defaultRequest.url.build()
                if (context.url.host.isEmpty()) {
                    mergeUrls(defaultUrl, context.url)
                }
                defaultRequest.attributes.allKeys.forEach {
                    if (!context.attributes.contains(it)) {
                        @Suppress("UNCHECKED_CAST")
                        context.attributes.put(it as AttributeKey<Any>, defaultRequest.attributes[it])
                    }
                }
                context.headers.appendMissing(defaultRequest.headers.build())
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

    /**
     * Configuration object for [DefaultRequestBuilder] plugin
     */
    public class DefaultRequestBuilder internal constructor() : HttpMessageBuilder {

        override val headers: HeadersBuilder = HeadersBuilder()
        public val url: URLBuilder = URLBuilder()
        public val attributes: Attributes = Attributes(concurrent = true)

        /**
         * Executes a [block] that configures the [URLBuilder] associated to this request.
         */
        public fun url(block: URLBuilder.() -> Unit): Unit = block(url)

        /**
         * Sets the [url] using the specified [scheme], [host], [port] and [path].
         * Pass `null` to keep existing value in the [URLBuilder].
         */
        public fun url(
            scheme: String? = null,
            host: String? = null,
            port: Int? = null,
            path: String? = null,
            block: URLBuilder.() -> Unit = {}
        ) {
            url.set(scheme, host, port, path, block)
        }

        /**
         * Sets the [HttpRequestBuilder.url] from [urlString].
         */
        public fun url(urlString: String) {
            url.takeFrom(urlString)
        }

        /**
         * Gets the associated URL's host.
         */
        public var host: String
            get() = url.host
            set(value) {
                url.host = value
            }

        /**
         * Gets the associated URL's port.
         */
        public var port: Int
            get() = url.port
            set(value) {
                url.port = value
            }

        /**
         * Sets attributes using [block].
         */
        public fun setAttributes(block: Attributes.() -> Unit) {
            attributes.apply(block)
        }
    }
}

/**
 * Set default request parameters. See [DefaultRequest]
 */
public fun HttpClientConfig<*>.defaultRequest(block: DefaultRequest.DefaultRequestBuilder.() -> Unit) {
    install(DefaultRequest) {
        block()
    }
}
