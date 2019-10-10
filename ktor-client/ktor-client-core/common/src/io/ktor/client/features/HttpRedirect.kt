/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlin.native.concurrent.*

@ThreadLocal
private val ALLOWED_FOR_REDIRECT: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head)

/**
 * [HttpClient] feature that handles http redirect
 *
 * @property rewritePostAsGet When true, will use GET when a POST request is redirected
 */
public class HttpRedirect {
    private val _checkHttpMethod = atomic(true)
    private val _allowHttpsDowngrade = atomic(false)
    private val _maximumRedirects = atomic(5)
    private val _rewritePostAsGet = atomic(false)

    /**
     * Check if the HTTP method is allowed for redirect.
     * Only [HttpMethod.Get] and [HttpMethod.Head] is allowed for implicit redirect.
     *
     * Please note: changing this flag could lead to security issues, consider changing the request URL instead.
     */
    public var checkHttpMethod: Boolean
        get() = _checkHttpMethod.value
        set(value) {
            _checkHttpMethod.value = value
        }

    /**
     * `true` value allows client redirect with downgrade from https to plain http.
     */
    public var allowHttpsDowngrade: Boolean
        get() = _allowHttpsDowngrade.value
        set(value) {
            _allowHttpsDowngrade.value = value
        }

    /**
     * Maximum number of times this feature will follow a http redirect before failing.
     * Must be less than or equal to the configured value of [HttpSend.maxSendCount] for any effect.
     *
     * RFC2068 recommends a maximum of five redirection.
     *
     * @see [HttpSend.maxSendCount]
     */
    public var maximumRedirects: Int
        get() = _maximumRedirects.value
        set(value) {
            _maximumRedirects.value = value
        }

    /**
     * When true, will use GET when a POST request is redirected
     *
     * RFC7231 6.4.2. 301 Moved Permanently & 6.4.3. 302 Found:
     * _a user agent **MAY** change the request method from POST to GET for the subsequent request_
     */
    public var rewritePostAsGet: Boolean
        get() = _rewritePostAsGet.value
        set(value) {
            _rewritePostAsGet.value = value
        }

    public companion object Feature : HttpClientFeature<HttpRedirect, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        /**
         * Occurs when received response with redirect message.
         */
        public val HttpResponseRedirect: EventDefinition<HttpResponse> = EventDefinition()

        override fun prepare(block: HttpRedirect.() -> Unit): HttpRedirect = HttpRedirect().apply(block)

        override fun install(feature: HttpRedirect, scope: HttpClient) {

            scope[HttpSend].intercept { origin, context ->
                val allowedMethods = ALLOWED_FOR_REDIRECT.toMutableList()
                if (feature.rewritePostAsGet) {
                    allowedMethods.add(HttpMethod.Post)
                }
                if (feature.checkHttpMethod && origin.request.method !in allowedMethods) {
                    return@intercept origin
                }

                handleCall(
                    context,
                    origin,
                    feature.allowHttpsDowngrade,
                    scope,
                    feature.maximumRedirects,
                    feature.rewritePostAsGet
                )
            }
        }
    }
}

private suspend fun Sender.handleCall(
    context: HttpRequestBuilder,
    origin: HttpClientCall,
    allowHttpsDowngrade: Boolean,
    client: HttpClient,
    maximumRedirects: Int,
    rewritePostAsGet: Boolean
): HttpClientCall {
    if (!origin.response.status.isRedirect()) return origin

    var redirects = 0
    var call = origin
    var requestBuilder = context
    val originProtocol = origin.request.url.protocol
    val originAuthority = origin.request.url.authority
    while (true) {
        client.monitor.raise(HttpRedirect.HttpResponseRedirect, call.response)
        val location = call.response.headers[HttpHeaders.Location]

        requestBuilder = HttpRequestBuilder().apply {
            takeFromWithExecutionContext(requestBuilder)
            url.parameters.clear()

            location?.let { url.takeFrom(it) }

            if (call.response.status.isRewritable() && rewritePostAsGet && call.request.method == HttpMethod.Post) {
                method = HttpMethod.Get
            }

            /**
             * Disallow redirect with a security downgrade.
             */
            if (!allowHttpsDowngrade && originProtocol.isSecure() && !url.protocol.isSecure()) {
                return call
            }

            if (originAuthority != url.authority) {
                headers.remove(HttpHeaders.Authorization)
            }
        }

        call = execute(requestBuilder)
        redirects++

        if (!call.response.status.isRedirect()) return call

        if (redirects >= maximumRedirects)
            throw SendCountExceedException("Max redirect count $maximumRedirects exceeded")
    }
}

private fun HttpStatusCode.isRedirect(): Boolean = when (value) {
    HttpStatusCode.MovedPermanently.value,
    HttpStatusCode.Found.value,
    HttpStatusCode.TemporaryRedirect.value,
    HttpStatusCode.PermanentRedirect.value,
    HttpStatusCode.SeeOther.value -> true
    else -> false
}

private fun HttpStatusCode.isRewritable(): Boolean = when (value) {
    HttpStatusCode.MovedPermanently.value,
    HttpStatusCode.Found.value -> true
    else -> false
}
