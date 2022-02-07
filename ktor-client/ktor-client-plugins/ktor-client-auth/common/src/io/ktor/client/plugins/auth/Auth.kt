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
 * A client's authentication plugin.
 * [providers] - list of auth providers to use.
 */
@KtorDsl
public class Auth private constructor(
    public val providers: MutableList<AuthProvider> = mutableListOf()
) {

    public companion object Plugin : HttpClientPlugin<Auth, Auth> {
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

            val circuitBreaker = AttributeKey<Unit>("auth-request")
            scope.plugin(HttpSend).intercept { context ->
                val origin = execute(context)
                if (origin.response.status != HttpStatusCode.Unauthorized) return@intercept origin
                if (origin.request.attributes.contains(circuitBreaker)) return@intercept origin

                var call = origin

                val candidateProviders = HashSet(plugin.providers)

                while (call.response.status == HttpStatusCode.Unauthorized) {
                    val headerValue = call.response.headers[HttpHeaders.WWWAuthenticate]

                    val authHeader = headerValue?.let { parseAuthorizationHeader(headerValue) }
                    val provider = when {
                        authHeader == null && candidateProviders.size == 1 -> candidateProviders.first()
                        authHeader == null -> return@intercept call
                        else -> candidateProviders.find { it.isApplicable(authHeader) } ?: return@intercept call
                    }
                    if (!provider.refreshToken(call.response)) return@intercept call

                    candidateProviders.remove(provider)

                    val request = HttpRequestBuilder()
                    request.takeFromWithExecutionContext(context)
                    provider.addRequestHeaders(request, authHeader)
                    request.attributes.put(circuitBreaker, Unit)

                    call = execute(request)
                }
                return@intercept call
            }
        }
    }
}

/**
 * Install [Auth] plugin.
 */
public fun HttpClientConfig<*>.Auth(block: Auth.() -> Unit) {
    install(Auth, block)
}
