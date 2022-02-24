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
