/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.features.DefaultRequest.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * [Feature] is used to set request default parameters.
 */
public class DefaultRequest(private val builder: Builder.() -> Unit) {

    public class Builder(requested: HttpRequestBuilder) : HttpRequestBuilder() {
        init {
            takeFrom(requested)
        }

        /**
         * Set the [baseUrl] to use it in sub-requests.
         * To override the existing [baseURL] in a sub-request, the full [URL] is needed in the [HttpRequestBuilder].
         *
         * The given [baseUrl] cannot have a query or a fragment part.
         */
        public fun baseURL(baseUrl: String) {
            baseURL(URLBuilder().takeFrom(baseUrl))
        }

        /**
         * Set the [baseURL] to use it in sub-requests.
         * To override the existing [baseURL] in a sub-request, the full [URL] is needed in the [HttpRequestBuilder].
         *
         * The given [baseURL] cannot have a query or a fragment part.
         */
        public fun baseURL(baseUrl: URLBuilder) {
            require(baseUrl.parameters.build() == Parameters.Empty && baseUrl.fragment.isEmpty()) {
                "The baseURL cannot have a query or a fragment"
            }
            url {
                if (isRelativeURL()) {
                    val requestedPath = encodedPath.removePrefix("/")
                    takeFrom(baseUrl)
                    encodedPath += if (encodedPath.endsWith("/")) {
                        requestedPath
                    } else {
                        "/$requestedPath"
                    }
                }
            }
        }

        private fun URLBuilder.isRelativeURL(): Boolean {
            val defaultURLBuilder = URLBuilder()
            val isDefault = this == defaultURLBuilder
            val isRelativeURL = protocol == defaultURLBuilder.protocol && authority == defaultURLBuilder.authority
            return !isDefault && isRelativeURL
        }
    }

    public companion object Feature : HttpClientFeature<Builder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: Builder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(feature: DefaultRequest, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                context.apply {
                    install(feature.builder)
                }
            }
        }

        internal fun HttpRequestBuilder.install(builder: Builder.() -> Unit) {
            val defaultRequest = Builder(this).apply(builder)
            takeFrom(defaultRequest)
        }
    }
}

/**
 * Set request default parameters.
 */
public fun HttpClientConfig<*>.defaultRequest(block: Builder.() -> Unit) {
    install(DefaultRequest) {
        block()
    }
}
