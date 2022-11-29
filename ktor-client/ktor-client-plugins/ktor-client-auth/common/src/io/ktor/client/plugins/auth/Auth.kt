/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*

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
    public val providers: MutableList<AuthProvider> = mutableListOf()
) {

    public companion object Plugin : HttpClientPlugin<Auth, Auth> {
        /**
         * Shows that request should skip auth and refresh token procedure.
         */
        public val AuthCircuitBreaker: AttributeKey<Unit> = AttributeKey("auth-request")

        override val key: AttributeKey<Auth> = AttributeKey("DigestAuth")

        override fun prepare(block: Auth.() -> Unit): Auth {
            return Auth().apply(block)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: Auth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                plugin.providers.filter { it.sendWithoutRequest(context) }.forEach {
                    it.addRequestHeaders(context)
                }
            }

            scope.plugin(HttpSend).intercept { context ->
                val origin = execute(context)
                if (origin.response.status != HttpStatusCode.Unauthorized) return@intercept origin
                if (origin.request.attributes.contains(AuthCircuitBreaker)) return@intercept origin

                var call = origin

                val candidateProviders = HashSet(plugin.providers)

                while (call.response.status == HttpStatusCode.Unauthorized) {
                    val headerValues = call.response.headers.getAll(HttpHeaders.WWWAuthenticate)
                    val authHeaders = headerValues?.map { parseAuthorizationHeaders(it) }?.flatten() ?: emptyList()

                    val (provider, authHeader) = when {
                        authHeaders.isEmpty() && candidateProviders.size == 1 -> candidateProviders.first() to null
                        authHeaders.isEmpty() -> return@intercept call
                        else -> findProviderAndHeader(candidateProviders, authHeaders) ?: return@intercept call
                    }
                    if (!provider.refreshToken(call.response)) return@intercept call

                    candidateProviders.remove(provider)

                    val request = HttpRequestBuilder()
                    request.takeFromWithExecutionContext(context)
                    provider.addRequestHeaders(request, authHeader)
                    request.attributes.put(AuthCircuitBreaker, Unit)

                    call = execute(request)
                }
                return@intercept call
            }
        }

        private fun findProviderAndHeader(
            providers: Collection<AuthProvider>,
            authHeaders: List<HttpAuthHeader>
        ): Pair<AuthProvider, HttpAuthHeader>? {
            authHeaders.forEach { header ->
                providers.forEach { provider ->
                    if (provider.isApplicable(header)) {
                        return provider to header
                    }
                }
            }

            return null
        }
    }
}

/**
 * Install [Auth] plugin.
 */
public fun HttpClientConfig<*>.Auth(block: Auth.() -> Unit) {
    install(Auth, block)
}
