/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * [HttpClient] feature that handles http redirect
 */
class HttpRedirect {
    companion object Feature : HttpClientFeature<Unit, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        override fun prepare(block: Unit.() -> Unit): HttpRedirect = HttpRedirect()

        override fun install(feature: HttpRedirect, scope: HttpClient) {
            scope.feature(HttpSend)!!.intercept { origin ->
                handleCall(origin)
            }
        }

        private suspend fun Sender.handleCall(origin: HttpClientCall): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var call = origin
            while (true) {
                val location = call.response.headers[HttpHeaders.Location]

                val requestBuilder = HttpRequestBuilder().apply {
                    takeFrom(origin.request)
                    url.parameters.clear()

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
