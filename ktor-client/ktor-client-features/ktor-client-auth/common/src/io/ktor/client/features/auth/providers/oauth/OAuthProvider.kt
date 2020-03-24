/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers.oauth

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*

const val DEFAULT_MAX_REQUEST_TRIES = 3 // original request, refreshToken, username/password
const val REQUEST_TRIES_HEADER = "NumRequestTries"

// `AuthScheme.OAuth` is currently wrong, so we use our own (see https://github.com/ktorio/ktor/pull/1733)
val AuthScheme.Bearer: String
    get() = "Bearer"

/**
 * Add [OAuthProvider] to client [Auth] providers.
 *
 * This provider is structured like the other ones in [io.ktor.client.features.auth.providers]
 * but *requires* a [TokenProvider] (which is therefore enforced as a parameter in [Auth.oauth]
 * and not found in [OAuthConfig].
 */
fun Auth.oauth(tokenProvider: TokenProvider, block: OAuthConfig.() -> Unit) {
    with(OAuthConfig().apply(block)) {
        providers.add(OAuthProvider(tokenProvider, maxTries, realm))
    }
}

/**
 * [OAuthProvider] configuration.
 */
class OAuthConfig {

    /**
     * Number of tries before the request is no longer retried.
     * `0` means that the request is only send once and this provider will not add an Authorization-header.
     */
    var maxTries: Int = DEFAULT_MAX_REQUEST_TRIES

    /**
     * Optional: current provider realm
     */
    var realm: String? = null
}

/**
 * Client basic authentication provider.
 */
class OAuthProvider(
    private val tokenProvider: TokenProvider,
    private var maxTries: Int,
    private val realm: String? = null
) : AuthProvider {

    // set to `false` so this provider is not ignored by [Auth] and retries are possible
    override val sendWithoutRequest = false

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Bearer) return false

        if (realm != null) {
            if (auth !is HttpAuthHeader.Parameterized) return false
            return auth.parameter("realm") == realm
        }

        return true
    }

    override suspend fun authenticate(request: HttpRequestBuilder): HttpRequestBuilder? {
        /**
         * Use [REQUEST_TRIES_HEADER] to store [tryCount].
         * Cannot use [AttributeKey] because those are not copied by [HttpRequestBuilder.takeFrom].
         */
        var tryCount =
            try {
                request.headers[REQUEST_TRIES_HEADER]?.toInt() ?: 0
            } catch (e: NumberFormatException) {
                0
            }

        if (++tryCount > maxTries) return null

        val token = tokenProvider.getToken() ?: return null
        val authorization = "${AuthScheme.Bearer} $token"

        return if (authorization == request.headers[HttpHeaders.Authorization]) {
            /**
             * Check, if this exact token has already been added to the request but we've still received a 401.
             * If so, the token did not work and should be invalidated.
             *
             * But still return the request (and not null) in this case
             * so [authenticate] is called again and we can try a new token
             * (which should be requested by the [TokenProvider] after the old token has been invalidated).
             */
            tokenProvider.invalidateToken(token)
            request

        } else {
            // add Authorization-header
            request.apply { headers[HttpHeaders.Authorization] = authorization }

        }.apply {
            // store retryCount in a header
            headers[REQUEST_TRIES_HEADER] = tryCount.toString()
        }
    }
}
