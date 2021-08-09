/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

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
 * [HttpClient] plugin that handles http redirect
 */
public class HttpRedirect {
    private val _checkHttpMethod = atomic(true)
    private val _allowHttpsDowngrade = atomic(false)

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

    public companion object Plugin : HttpClientPlugin<HttpRedirect, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        /**
         * Occurs when received response with redirect message.
         */
        public val HttpResponseRedirect: EventDefinition<HttpResponse> = EventDefinition()

        override fun prepare(block: HttpRedirect.() -> Unit): HttpRedirect = HttpRedirect().apply(block)

        override fun install(plugin: HttpRedirect, scope: HttpClient) {
            scope[HttpSend].intercept { context ->
                val origin = execute(context)
                if (plugin.checkHttpMethod && origin.request.method !in ALLOWED_FOR_REDIRECT) {
                    return@intercept origin
                }

                handleCall(context, origin, plugin.allowHttpsDowngrade, scope)
            }
        }

        @OptIn(InternalAPI::class)
        private suspend fun Sender.handleCall(
            context: HttpRequestBuilder,
            origin: HttpClientCall,
            allowHttpsDowngrade: Boolean,
            client: HttpClient
        ): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var call = origin
            var requestBuilder = context
            val originProtocol = origin.request.url.protocol
            val originAuthority = origin.request.url.authority

            while (true) {
                client.monitor.raise(HttpResponseRedirect, call.response)

                val location = call.response.headers[HttpHeaders.Location]

                requestBuilder = HttpRequestBuilder().apply {
                    takeFromWithExecutionContext(requestBuilder)
                    url.parameters.clear()

                    location?.let { url.takeFrom(it) }

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
                if (!call.response.status.isRedirect()) return call
            }
        }
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
