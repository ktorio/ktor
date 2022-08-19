/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.util.*

private val ALLOWED_FOR_REDIRECT: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head)

@OptIn(InternalAPI::class)
internal val HttpRedirectPlugin = createClientPlugin("HttpRedirect", HttpRedirect::Config) {

    val checkHttpMethod: Boolean = pluginConfig.checkHttpMethod
    val allowHttpsDowngrade: Boolean = pluginConfig.allowHttpsDowngrade

    suspend fun Sender.handleCall(
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
            client.monitor.raise(HttpRedirect.HttpResponseRedirect, call.response)

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

    on(Send) { request ->
        val origin = execute(request)
        if (checkHttpMethod && origin.request.method !in ALLOWED_FOR_REDIRECT) {
            return@on origin
        }

        handleCall(request, origin, allowHttpsDowngrade, client)
    }
}

/**
 * An [HttpClient] plugin that handles HTTP redirects
 */
public class HttpRedirect private constructor() {

    @KtorDsl
    public class Config {

        /**
         * Checks whether the HTTP method is allowed for the redirect.
         * Only [HttpMethod.Get] and [HttpMethod.Head] are allowed for implicit redirection.
         *
         * Please note: changing this flag could lead to security issues, consider changing the request URL instead.
         */
        public var checkHttpMethod: Boolean = true

        /**
         * `true` allows a client to make a redirect with downgrading from HTTPS to plain HTTP.
         */
        public var allowHttpsDowngrade: Boolean = false
    }

    public companion object Plugin : HttpClientPlugin<Config, ClientPluginInstance<Config>> {
        override val key: AttributeKey<ClientPluginInstance<Config>> = AttributeKey("HttpRedirect")

        /**
         * Occurs when receiving a response with a redirect message.
         */
        public val HttpResponseRedirect: EventDefinition<HttpResponse> = EventDefinition()

        override fun prepare(block: Config.() -> Unit): ClientPluginInstance<Config> {
            return HttpRedirectPlugin.prepare(block)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ClientPluginInstance<Config>, scope: HttpClient) {
            plugin.install(scope)
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
