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
class Auth(
    val providers: MutableList<AuthProvider> = mutableListOf()
) {
    private val alwaysSend by lazy { providers.filter { it.sendWithoutRequest } }

    companion object Feature : HttpClientFeature<Auth, Auth> {
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

            scope.feature(HttpSend)!!.intercept { origin, context ->
                var call = origin

                val usedProviders = mutableSetOf<AuthProvider>()
                while (call.response.status == HttpStatusCode.Unauthorized) {
                    val headerValue = call.response.headers[HttpHeaders.WWWAuthenticate] ?: return@intercept call
                    val authHeader = parseAuthorizationHeader(headerValue) ?: return@intercept call
                    val provider = feature.providers.find { it.isApplicable(authHeader) } ?: return@intercept call

                    if (provider in usedProviders || provider in feature.alwaysSend) return@intercept call

                    val request = HttpRequestBuilder()
                    request.takeFrom(context)
                    provider.addRequestHeaders(request)

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
fun HttpClientConfig<*>.Auth(block: Auth.() -> Unit) {
    install(Auth, block)
}
