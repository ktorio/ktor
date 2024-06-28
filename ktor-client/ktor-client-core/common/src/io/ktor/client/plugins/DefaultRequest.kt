/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.DefaultRequest")

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
 *       base = `https://example.com/dir/default_file.html`,
 *       request = `file.html`,
 *       result = `https://example.com/dir/file.html`
 * 2. Request URL starts with slash -> use request path as is.
 *   Example:
 *   base = `https://example.com/dir/default_file.html`,
 *   request = `/root/file.html`,
 *   result = `https://example.com/root/file.html`
 *
 * Headers of the builder will be pre-populated with request headers.
 * You can use [HeadersBuilder.contains], [HeadersBuilder.appendIfNameAbsent]
 * and [HeadersBuilder.appendIfNameAndValueAbsent] to avoid appending some header twice.
 *
 * Usage:
 * ```
 * val client = HttpClient {
 *   defaultRequest {
 *     url("https://base.url/dir/")
 *     headers.appendIfNameAbsent(HttpHeaders.ContentType, ContentType.Application.Json)
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
                val originalUrlString = context.url.toString()
                val defaultRequest = DefaultRequestBuilder().apply {
                    headers.appendAll(this@intercept.context.headers)
                    plugin.block(this)
                }
                context.uri = mergeUris(
                    defaultRequest.uri.asUri(),
                    context.uri.asRelativeUri(),
                )

                defaultRequest.attributes.allKeys.forEach {
                    if (!context.attributes.contains(it)) {
                        @Suppress("UNCHECKED_CAST")
                        context.attributes.put(it as AttributeKey<Any>, defaultRequest.attributes[it])
                    }
                }

                context.headers.clear()
                context.headers.appendAll(defaultRequest.headers.build())

                LOGGER.trace("Applied DefaultRequest to $originalUrlString. New url: ${context.url}")
            }
        }

        private fun mergeUris(defaults: UriReference, request: UriReference): Locator {
            return Uri(
                protocol = request.protocol ?: defaults.protocol,
                protocolSeparator = request.protocolSeparator ?: defaults.protocolSeparator,
                authority = Uri.Authority(
                    request.authority?.userInfo ?: defaults.authority?.userInfo,
                    request.host ?: defaults.host,
                    request.port ?: defaults.port,
                ),
                path = when {
                    request.path.isAbsolute() -> request.path
                    request.path.isRelative() -> Uri.Path(
                        defaults.path.segments.dropLast(1) + request.path.segments
                    )
                    else -> defaults.path
                },
                parameters = mergeParameters(defaults.parameters, request.parameters),
                fragment = request.fragment ?: defaults.fragment
            )
        }

        private fun mergeParameters(defaults: Parameters?, request: Parameters?): Parameters? =
            if (request == null && defaults == null)
                null
            else Parameters.build {
                appendAll(defaults.orEmpty())
                appendAll(request.orEmpty())
            }

        private fun mergeUrls(defaultUrl: Url, requestUrl: UrlBuilder) {
            if (requestUrl.protocolOrNull == null) {
                requestUrl.protocolOrNull = defaultUrl.protocol
            }
            if (requestUrl.host.isNotEmpty()) return

            val tempFromDefault = URLBuilder(defaultUrl)
            with(requestUrl) {
                tempFromDefault.protocolOrNull = protocolOrNull
                if (port != DEFAULT_PORT) {
                    tempFromDefault.port = port
                }

                tempFromDefault.encodedPathSegments = concatenatePath(tempFromDefault.encodedPathSegments, encodedPathSegments)

                if (encodedFragment.isNotEmpty()) {
                    tempFromDefault.encodedFragment = encodedFragment
                }

                // 1. Make a params builder with default parameters
                val defaultParameters = ParametersBuilder().apply {
                    appendAll(tempFromDefault.encodedParameters)
                }

                // 2. Set result params to request
                tempFromDefault.encodedParameters = encodedParameters

                // 3. Add default params where keys match
                defaultParameters.entries().forEach { (key, values) ->
                    if (!tempFromDefault.encodedParameters.contains(key)) {
                        tempFromDefault.encodedParameters.appendAll(key, values)
                    }
                }

                // 4. Add request + default params to request
                takeFrom(tempFromDefault)
            }
        }

        private fun concatenatePath(parent: List<String>, child: List<String>): List<String> {
            if (child.isEmpty()) return parent
            if (parent.isEmpty()) return child

            // Path starts from "/"
            if (child.first().isEmpty()) return child

            return buildList(parent.size + child.size - 1) {
                for (index in 0 until parent.size - 1) {
                    add(parent[index])
                }

                addAll(child)
            }
        }
    }

    /**
     * Configuration object for [DefaultRequestBuilder] plugin
     */
    @KtorDsl
    public class DefaultRequestBuilder internal constructor(
        public val attributes: Attributes = Attributes(concurrent = true),
        override val headers: HeadersBuilder = HeadersBuilder()
    ) : HttpMessageBuilder {

        public val url: UrlBuilder get() =
            uri as? UrlBuilder ?: URLBuilder(uri).also {
                uri = it
            }

        public var uri: Locator = UrlBuilder()

        /**
         * Executes a [block] that configures the [UrlBuilder] associated to this request.
         */
        public fun url(block: UrlBuilder.() -> Unit): Unit = block(url)

        /**
         * Sets the [url] using the specified [scheme], [host], [port] and [path].
         * Pass `null` to keep existing value in the [UrlBuilder].
         */
        public fun url(
            scheme: String? = null,
            host: String? = null,
            port: Int? = null,
            path: String? = null,
            block: UrlBuilder.() -> Unit = {}
        ) {
            url.set(scheme, host, port, path, block)
        }

        /**
         * Sets the [HttpRequestBuilder.url] from [urlString].
         */
        public fun url(urlString: String) {
            uri = LocatorString(urlString)
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
