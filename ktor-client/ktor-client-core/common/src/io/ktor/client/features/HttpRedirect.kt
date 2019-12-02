/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.jvm.*
import kotlin.native.concurrent.*

@ThreadLocal
private val ALLOWED_FOR_REDIRECT: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head)

/**
 * [HttpClient] feature that handles http redirect
 */
class HttpRedirect {
    /**
     * Check if the HTTP method is allowed for redirect.
     * Only [HttpMethod.Get] and [HttpMethod.Head] is allowed for implicit redirect.
     *
     * Please note: changing this flag could lead to security issues, consider changing the request URL instead.
     */
    @KtorExperimentalAPI
    @Volatile
    var checkHttpMethod: Boolean = true

    companion object Feature : HttpClientFeature<Unit, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        override fun prepare(block: Unit.() -> Unit): HttpRedirect = HttpRedirect()

        override fun install(feature: HttpRedirect, scope: HttpClient) {
            scope.feature(HttpSend)!!.intercept { origin ->
                if (feature.checkHttpMethod && origin.request.method !in ALLOWED_FOR_REDIRECT) {
                    return@intercept origin
                }

                handleCall(origin)
            }
        }

        private suspend fun Sender.handleCall(origin: HttpClientCall): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var call = origin
            val originProtocol = origin.request.url.protocol
            val originAuthority = origin.request.url.authority
            while (true) {
                val location = call.response.headers[HttpHeaders.Location]

                val requestBuilder = HttpRequestBuilder().apply {
                    takeFrom(origin.request)
                    url.parameters.clear()

                    /**
                     * Disallow redirect with a security downgrade.
                     */
                    if (originProtocol.isSecure() && !url.protocol.isSecure()) {
                        return call
                    }

                    if (originAuthority != url.authority) {
                        headers.remove(HttpHeaders.Authorization)
                    }

                    location?.let { url.takeFrom(it) }
                }

                call = execute(requestBuilder)
                if (!call.response.status.isRedirect()) return call
            }
        }
    }
}

private fun HttpStatusCode.isRedirect(): Boolean = when (value) {
    HttpStatusCode.MovedPermanently.value,
    HttpStatusCode.Found.value,
    HttpStatusCode.TemporaryRedirect.value,
    HttpStatusCode.PermanentRedirect.value -> true
    else -> false
}
