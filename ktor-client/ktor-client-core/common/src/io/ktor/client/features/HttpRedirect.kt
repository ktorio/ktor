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
 *
 * @property maximumRedirects Maximum number of times this feature will follow a http redirect before failing
 * @property rewritePostAsGet When true, will use GET when a POST request is redirected
 */
class HttpRedirect(val maximumRedirects: Int, val rewritePostAsGet: Boolean) {

    class Config {
        /**
         * Maximum number of times this feature will follow a http redirect before failing.
         * Must be less than or equal to the configured value of [HttpSend.maxSendCount] for any effect.
         *
         * RFC2068 recommends a maximum of five redirection.
         *
         * @see [HttpRedirect.maximumRedirects]
         * @see [HttpSend.maxSendCount]
         */
        var maximumRedirects: Int = 5

        /**
         * When true, will use GET when a POST request is redirected
         *
         * RFC7231 6.4.2. 301 Moved Permanently & 6.4.3. 302 Found:
         * _a user agent **MAY** change the request method from POST to GET for the subsequent request_
         *
         * @see [HttpRedirect.rewritePostAsGet]
         */
        var rewritePostAsGet: Boolean = false
    }

    companion object Feature : HttpClientFeature<Config, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        override fun prepare(block: Config.() -> Unit): HttpRedirect {
            val config = Config().apply(block)
            return HttpRedirect(config.maximumRedirects, config.rewritePostAsGet)
        }

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

                if (redirects >= feature.maximumRedirects) throw RedirectCountExceedException("Max redirect count ${feature.maximumRedirects} exceeded")
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

/**
 * Thrown when too many redirects are followed.
 * It could be caused by infinite or too long redirect sequence.
 * Maximum number of requests is limited by [HttpRedirect.maximumRedirects]
 */
class RedirectCountExceedException(message: String) : IllegalStateException(message)
