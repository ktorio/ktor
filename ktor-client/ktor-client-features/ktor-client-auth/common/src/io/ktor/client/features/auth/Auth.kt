/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*

/**
 * Client authentication feature.
 * [providers] - list of auth providers to use.
 */
@Suppress("KDocMissingDocumentation")
public class Auth(
    public val providers: MutableList<AuthProvider> = mutableListOf()
) {
    private val alwaysSend by lazy { providers.filter { it.sendWithoutRequest } }

    public companion object Feature : HttpClientFeature<Auth, Auth> {
        override val key: AttributeKey<Auth> = AttributeKey("DigestAuth")

        override fun prepare(block: Auth.() -> Unit): Auth {
            return Auth().apply(block)
        }

        override fun install(feature: Auth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                for (provider in feature.alwaysSend) {
                    provider.addRequestHeaders(context)
                }
            }

            val circuitBreaker = AttributeKey<Unit>("auth-request")
            scope.feature(HttpSend)!!.intercept { origin, context ->
                if (origin.response.status != HttpStatusCode.Unauthorized) return@intercept origin
                if (origin.request.attributes.contains(circuitBreaker)) return@intercept origin

                var call = origin
                val candidateProviders = HashSet(feature.providers).apply { removeAll(feature.alwaysSend) }
                while (call.response.status == HttpStatusCode.Unauthorized) {
                    val headerValue = call.response.headers[HttpHeaders.WWWAuthenticate] ?: return@intercept call
                    val authHeader = parseAuthorizationHeader(headerValue) ?: return@intercept call
                    val provider = candidateProviders.find { it.isApplicable(authHeader) } ?: return@intercept call
                    candidateProviders.remove(provider)

                    val request = HttpRequestBuilder()
                    request.takeFromWithExecutionContext(context)
                    provider.addRequestHeaders(request)
                    request.attributes.put(circuitBreaker, Unit)

                    call = execute(request)
                }
                return@intercept call
            }
        }
    }
}

/**
 * Install [Auth] feature.
 */
public fun HttpClientConfig<*>.Auth(block: Auth.() -> Unit) {
    install(Auth, block)
}
