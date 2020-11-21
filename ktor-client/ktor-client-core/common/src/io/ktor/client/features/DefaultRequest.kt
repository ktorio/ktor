/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.util.*

/**
 * [Feature] is used to set request default parameters.
 */
public class DefaultRequest(private val builder: HttpRequestBuilder.() -> Unit) {

    public companion object Feature : HttpClientFeature<HttpRequestBuilder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: HttpRequestBuilder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(feature: DefaultRequest, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                context.apply(feature.builder)
            }
        }
    }
}

/**
 * Set request default parameters.
 */
public fun HttpClientConfig<*>.defaultRequest(block: HttpRequestBuilder.() -> Unit) {
    install(DefaultRequest) {
        block()
    }
}

/**
 * Set the [baseUrl] to use it in sub-requests.
 * To override the existing [baseURL] in a sub-request, the full [URL] is needed in the [HttpRequestBuilder].
 *
 * The given [baseUrl] cannot have a query or a fragment part.
 * The given [baseUrl] cannot be the default [URLBuilder].
 */
public fun HttpRequestBuilder.baseURL(baseUrl: String) {
    baseURL(URLBuilder().takeFrom(baseUrl))
}

/**
 * Set the [baseUrlBuilder] to use it in sub-requests.
 * To override the existing [baseURL] in a sub-request, the full [URL] is needed in the [HttpRequestBuilder].
 *
 * The given [baseUrlBuilder] cannot have a query or a fragment part.
 * The given [baseUrlBuilder] cannot be the default [URLBuilder].
 */
public fun HttpRequestBuilder.baseURL(baseUrlBuilder: URLBuilder) {
    val defaultURLBuilder = URLBuilder()
    require(defaultURLBuilder != baseUrlBuilder) { "The given baseUrlBuilder $baseUrlBuilder cannot be the default URLBuilder $defaultURLBuilder"}
    require(baseUrlBuilder.parameters.build() == Parameters.Empty && baseUrlBuilder.fragment == "") {
        "The baseURL cannot have a query or a fragment"
    }

    url {
        if(this != defaultURLBuilder && startsWith(defaultURLBuilder)) {
            val requestedPath = encodedPath
            takeFrom(baseUrlBuilder)
            encodedPath += "/$requestedPath"
        }
    }
}

internal fun URLBuilder.startsWith(other: URLBuilder): Boolean =
    protocol == other.protocol
        && host == other.host
        && port == other.port
        && user == other.user
        && password == other.password
