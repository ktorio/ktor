/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*

public class AuthConfig {
    public val providers: MutableList<AuthProvider> = mutableListOf()
}

public val AuthPlugin: ClientPlugin<AuthConfig> = createClientPlugin("Auth", ::AuthConfig) {
    val providers = pluginConfig.providers.toList()

    onRequest { request, _ ->
        addDefaultHeaders(providers, request)
    }

    on(Send) { request ->
        sendRequest(providers, request)
    }
}

/**
 * A client's plugin that handles authentication and authorization.
 * Typical usage scenarios include logging in users and gaining access to specific resources.
 *
 * You can learn more from [Authentication and authorization](https://ktor.io/docs/auth.html).
 *
 * [providers] - list of auth providers to use.
 */
@KtorDsl
public class Auth private constructor(
    public val providers: List<AuthProvider>
) {

    public companion object Plugin : HttpClientPlugin<AuthConfig, Auth> {
        /**
         * Shows that request should skip auth and refresh token procedure.
         */
        public val AuthCircuitBreaker: AttributeKey<Unit> = AttributeKey("auth-request")

        override val key: AttributeKey<Auth> = AttributeKey("DigestAuth")

        override fun prepare(block: AuthConfig.() -> Unit): Auth {
            return Auth(AuthConfig().apply(block).providers.toList())
        }

        override fun install(plugin: Auth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                addDefaultHeaders(plugin.providers, context)
            }

            scope.plugin(HttpSend).intercept { context ->
                return@intercept sendRequest(plugin.providers, context)
            }
        }
    }
}

private suspend fun addDefaultHeaders(providers: List<AuthProvider>, request: HttpRequestBuilder) {
    providers.filter { it.sendWithoutRequest(request) }.forEach {
        it.addRequestHeaders(request)
    }
}

@OptIn(InternalAPI::class)
private suspend fun Sender.sendRequest(
    providers: List<AuthProvider>,
    originalRequest: HttpRequestBuilder
): HttpClientCall {
    val origin = execute(originalRequest)
    if (origin.response.status != HttpStatusCode.Unauthorized) return origin
    if (origin.request.attributes.contains(Auth.AuthCircuitBreaker)) return origin

    var call = origin

    val candidateProviders = HashSet(providers)

    while (call.response.status == HttpStatusCode.Unauthorized) {
        val headerValue = call.response.headers[HttpHeaders.WWWAuthenticate]

        val authHeader = headerValue?.let { parseAuthorizationHeader(headerValue) }
        val provider = when {
            authHeader == null && candidateProviders.size == 1 -> candidateProviders.first()
            authHeader == null -> return call
            else -> candidateProviders.find { it.isApplicable(authHeader) } ?: return call
        }
        if (!provider.refreshToken(call.response)) return call

        candidateProviders.remove(provider)

        val request = HttpRequestBuilder()
        request.takeFromWithExecutionContext(request)
        provider.addRequestHeaders(request, authHeader)
        request.attributes.put(Auth.AuthCircuitBreaker, Unit)

        call = execute(request)
    }
    return call
}

/**
 * Install [Auth] plugin.
 */
public fun HttpClientConfig<*>.Auth(block: AuthConfig.() -> Unit) {
    install(Auth, block)
}
