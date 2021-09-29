/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.plugins.DefaultRequest.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * [Plugin] is used to set request default parameters.
 */
public class DefaultRequest private constructor(private val block: HttpRequestBuilder.() -> Unit) {

    public companion object Plugin : HttpClientPlugin<HttpRequestBuilder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: HttpRequestBuilder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(plugin: DefaultRequest, scope: HttpClient) {
            val defaultRequest = HttpRequestBuilder().apply(plugin.block).build()
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val newUrl = with(context.url) {
                    if (host.isEmpty()) {
                        val url = URLBuilder(defaultRequest.url)
                        if (protocol != URLProtocol.HTTP) url.protocol = protocol
                        if (port != DEFAULT_PORT) url.port = port
                        if (user != null) url.encodedUser = encodedUser
                        if (password != null) url.encodedPassword = encodedPassword
                        if (encodedPathSegments.size > 1 && encodedPathSegments.first().isEmpty()) {
                            // path starts from "/"
                            url.encodedPathSegments = encodedPathSegments
                        } else if (encodedPathSegments.size != 1 || encodedPathSegments.first().isNotEmpty()) {
                            url.encodedPathSegments = url.encodedPathSegments.dropLast(1) + encodedPathSegments
                        }
                        url.encodedFragment = encodedFragment
                        url.encodedParameters = encodedParameters
                        url
                    } else {
                        URLBuilder().takeFrom(context.url)
                    }
                }
                context.headers.appendMissing(defaultRequest.headers)
                context.url.takeFrom(newUrl)
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
