/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.utils.io.*

/**
 * Installs the client's [BearerAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.bearer)
 */
public fun AuthConfig.bearer(block: BearerAuthConfig.() -> Unit) {
    with(BearerAuthConfig().apply(block)) {
        this@bearer.providers.add(
            BearerAuthProvider(refreshTokens, loadTokens, sendWithoutRequest, realm, cacheTokens, tokenStorage)
        )
    }
}

public class BearerTokens(
    public val accessToken: String,
    public val refreshToken: String?
)

/**
 * Parameters to be passed to [BearerAuthConfig.refreshTokens] lambda.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.RefreshTokensParams)
 */
public class RefreshTokensParams(
    public val client: HttpClient,
    public val response: HttpResponse,
    public val oldTokens: BearerTokens?
) {

    /**
     * Marks that this request is for refreshing auth tokens, resulting in a special handling of it.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.RefreshTokensParams.markAsRefreshTokenRequest)
     */
    public fun HttpRequestBuilder.markAsRefreshTokenRequest() {
        attributes.put(AuthCircuitBreaker, Unit)
    }
}

/**
 * A configuration for [BearerAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig)
 */
@KtorDsl
public class BearerAuthConfig {
    internal var refreshTokens: suspend RefreshTokensParams.() -> BearerTokens? = { null }
    internal var loadTokens: suspend () -> BearerTokens? = { null }
    internal var sendWithoutRequest: (HttpRequestBuilder) -> Boolean = { true }
    internal var tokenStorage: TokenStorage<BearerTokens>? = null

    public var realm: String? = null

    /**
     * Controls whether to cache tokens between requests.
     * When set to false, the provider will call [loadTokens] for each request.
     * Default value is true.
     */
    public var cacheTokens: Boolean = true

    /**
     * Configures a callback that refreshes a token when the 401 status code is received.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig.refreshTokens)
     */
    public fun refreshTokens(block: suspend RefreshTokensParams.() -> BearerTokens?) {
        refreshTokens = block
    }

    /**
     * Configures a callback that loads a cached token from a local storage.
     * Note: Using the same client instance here to make a request will result in a deadlock.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig.loadTokens)
     */
    public fun loadTokens(block: suspend () -> BearerTokens?) {
        loadTokens = block
    }

    /**
     * Provides a custom token storage implementation.
     * This allows for complete control over how tokens are stored, cached, and managed.
     *
     * When provided, this takes precedence over [cacheTokens] setting.
     *
     * Example usage:
     * ```kotlin
     * bearer {
     *     // Use a custom token storage implementation
     *     tokenStorage(customStorage)
     *     // Or use built-in implementations
     *     tokenStorage(TokenStorageFactory.withCache { myTokenProvider() })
     *     tokenStorage(TokenStorageFactory.nonCaching { myTokenProvider() })
     * }
     * ```
     *
     * @param storage a custom token storage implementation
     */
    public fun tokenStorage(storage: TokenStorage<BearerTokens>) {
        tokenStorage = storage
    }

    /**
     * Sends credentials without waiting for [HttpStatusCode.Unauthorized].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig.sendWithoutRequest)
     */
    public fun sendWithoutRequest(block: (HttpRequestBuilder) -> Boolean) {
        sendWithoutRequest = block
    }
}

/**
 * An authentication provider for the Bearer HTTP authentication scheme.
 * Bearer authentication involves security tokens called bearer tokens.
 * As an example, these tokens can be used as a part of OAuth flow to authorize users of your application
 * by using external providers, such as Google, Facebook, Twitter, and so on.
 *
 * You can control whether tokens are cached between requests with the [cacheTokens] parameter:
 * - When `true` (default), tokens are cached after the first request and reused.
 * - When `false`, [loadTokens] is called for each request, and the token is never cached.
 *
 * You can learn more from [Bearer authentication](https://ktor.io/docs/bearer-client.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider)
 */
public class BearerAuthProvider(
    private val refreshTokens: suspend RefreshTokensParams.() -> BearerTokens?,
    private val loadTokensCallback: suspend () -> BearerTokens?,
    private val sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean = { true },
    private val realm: String?,
    private val cacheTokens: Boolean = true,
    private val customTokenStorage: TokenStorage<BearerTokens>? = null
) : AuthProvider {

    @Suppress("OverridingDeprecatedMember")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    // Create the token storage based on configuration
    private val tokenStorage: TokenStorage<BearerTokens> = customTokenStorage
        ?: if (cacheTokens) {
            TokenStorageFactory.withCache(loadTokensCallback)
        } else {
            TokenStorageFactory.nonCaching(loadTokensCallback)
        }

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = sendWithoutRequestCallback(request)

    /**
     * Checks if current provider is applicable to the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider.isApplicable)
     */
    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Bearer) {
            LOGGER.trace("Bearer Auth Provider is not applicable for $auth")
            return false
        }
        val isSameRealm = when {
            realm == null -> true
            auth !is HttpAuthHeader.Parameterized -> false
            else -> auth.parameter("realm") == realm
        }
        if (!isSameRealm) {
            LOGGER.trace("Bearer Auth Provider is not applicable for this realm")
        }
        return isSameRealm
    }

    /**
     * Adds an authentication method headers and credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider.addRequestHeaders)
     */
    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        // If the request has the circuit breaker attribute, don't add any auth headers
        if (request.attributes.contains(AuthCircuitBreaker)) {
            LOGGER.trace("Circuit breaker active - no auth header will be added")
            return
        }

        // Get token from storage
        val token = tokenStorage.loadToken() ?: return
        LOGGER.trace("Using token for request: ${token.accessToken}")

        request.headers {
            val tokenValue = "Bearer ${token.accessToken}"
            if (contains(HttpHeaders.Authorization)) {
                remove(HttpHeaders.Authorization)
            }
            append(HttpHeaders.Authorization, tokenValue)
        }
    }

    public override suspend fun refreshToken(response: HttpResponse): Boolean {
        // Get the current token for use in refresh params
        val currentToken = tokenStorage.loadToken()

        // Update token with the result of refresh function
        val newToken = tokenStorage.updateToken {
            refreshTokens(RefreshTokensParams(response.call.client, response, currentToken))
        }

        if (newToken != null) {
            LOGGER.trace("Token refreshed: ${newToken.accessToken}")
            return true
        } else {
            LOGGER.trace("No refreshed token returned")
            return false
        }
    }

    /**
     * Clears the currently stored authentication tokens from the cache.
     *
     * This method should be called in the following cases:
     * - When access or refresh tokens have been updated externally
     * - When you want to clear sensitive token data (for example, during logout)
     *
     * Note: The result of `loadTokens` invocation is cached internally when [cacheTokens] is true.
     * Calling this method will force the next authentication attempt to fetch fresh tokens
     * through the configured `loadTokens` function.
     *
     * If [cacheTokens] is false, this method will clear any temporarily stored refresh token.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider.clearToken)
     */
    public suspend fun clearToken() {
        tokenStorage.clearToken()
    }
}
