/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * [HttpClient] feature that handles http redirect
 */
class HttpRedirect(config: Config) {
    val maximumRedirects: Int = config.maximumRedirects
    val rewritePostAsGet: Boolean = config.rewritePostAsGet

    class Config {
        /** RFC2068 recommends a maximum of five redirection */
        var maximumRedirects: Int = 5

        /** RFC7231 6.4.2. 301 Moved Permanently & 6.4.3. 302 Found:
        * _a user agent **MAY** change the request method from POST to GET for the subsequent request_ */
        var rewritePostAsGet: Boolean = false
    }

    companion object Feature : HttpClientFeature<Config, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        override fun prepare(block: Config.() -> Unit): HttpRedirect = HttpRedirect(Config().apply(block))

        override fun install(feature: HttpRedirect, scope: HttpClient) {
            scope.feature(HttpSend)!!.intercept { origin ->
                handleCall(feature, origin)
            }
        }

        private suspend fun Sender.handleCall(feature: HttpRedirect, origin: HttpClientCall): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var redirects = 0
            var call = origin
            while (true) {
                val location = call.response.headers[HttpHeaders.Location]

                call.close()

                call = execute(HttpRequestBuilder().apply {
                    takeFrom(origin.request)
                    url.parameters.clear()

                    location?.let { url.takeFrom(it) }

                    when (call.response.status.value) {
                        HttpStatusCode.MovedPermanently.value,
                        HttpStatusCode.Found.value -> {
                            if (feature.rewritePostAsGet && call.request.method == HttpMethod.Post) {
                                method = HttpMethod.Get
                            }
                        }
                    }
                })
                redirects++

                if (!call.response.status.isRedirect()) return call

                // TODO: Just return last call, return original (closed) call, or throw an exception?
                if (redirects > feature.maximumRedirects) return call
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
