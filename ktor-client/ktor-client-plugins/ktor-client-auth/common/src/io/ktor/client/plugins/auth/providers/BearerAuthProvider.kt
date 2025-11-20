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
            BearerAuthProvider(
                refreshTokens,
                loadTokens,
                sendWithoutRequest,
                realm,
                cacheTokens,
                nonCancellableRefresh
            )
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

    public var realm: String? = null

    /**
     * Configures whether to cache the result of [loadTokens].
     *
     * When `true` (default), the result of [loadTokens] is cached and reused for subsequent requests.
     * When `false`, [loadTokens] is called on every request, allowing external token management systems
     * (like Firebase Auth) to provide fresh tokens without redundant caching.
     *
     * Set this to `false` when:
     * - You manage token caching externally (e.g., Firebase Auth, or other authentication SDKs)
     * - You need fine-grained control over token lifecycle
     * - You want to avoid calling [BearerAuthProvider.clearToken] on logout
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig.cacheTokens)
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
     * Sends credentials without waiting for [HttpStatusCode.Unauthorized].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthConfig.sendWithoutRequest)
     */
    public fun sendWithoutRequest(block: (HttpRequestBuilder) -> Boolean) {
        sendWithoutRequest = block
    }

    /**
     * When enabled, token refresh function is executed in a NonCancellable coroutine context.
     * This prevents cancellation of the originating request from rolling back a successful token refresh.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.BearerAuthConfig.nonCancellableRefresh)
     */
    public var nonCancellableRefresh: Boolean = false
}

/**
 * An authentication provider for the Bearer HTTP authentication scheme.
 * Bearer authentication involves security tokens called bearer tokens.
 * As an example, these tokens can be used as a part of OAuth flow to authorize users of your application
 * by using external providers, such as Google, Facebook, Twitter, and so on.
 *
 * You can learn more from [Bearer authentication](https://ktor.io/docs/bearer-client.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider)
 */
public class BearerAuthProvider(
    private val refreshTokens: suspend RefreshTokensParams.() -> BearerTokens?,
    loadTokens: suspend () -> BearerTokens?,
    private val sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean = { true },
    private val realm: String?,
    cacheTokens: Boolean = true,
    private val nonCancellableRefresh: Boolean = false,
) : AuthProvider {

    public constructor(
        refreshTokens: suspend (RefreshTokensParams) -> BearerTokens?,
        loadTokens: suspend () -> BearerTokens?,
        sendWithoutRequestCallback: (HttpRequestBuilder) -> Boolean,
        realm: String?,
    ) : this(
        refreshTokens,
        loadTokens,
        sendWithoutRequestCallback,
        realm,
        cacheTokens = true,
        nonCancellableRefresh = false
    )

    @Suppress("OverridingDeprecatedMember")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    private val tokensHolder = AuthTokenHolder(loadTokens, cacheTokens)

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
        val token = tokensHolder.loadToken() ?: return

        request.headers {
            val tokenValue = "Bearer ${token.accessToken}"
            if (contains(HttpHeaders.Authorization)) {
                remove(HttpHeaders.Authorization)
            }
            if (request.attributes.contains(AuthCircuitBreaker).not()) {
                append(HttpHeaders.Authorization, tokenValue)
            }
        }
    }

    public override suspend fun refreshToken(response: HttpResponse): Boolean {
        val newToken = tokensHolder.setToken(nonCancellableRefresh) {
            refreshTokens(RefreshTokensParams(response.call.client, response, tokensHolder.loadToken()))
        }
        return newToken != null
    }

    /**
     * Clears the currently stored authentication tokens from the cache.
     *
     * This method should be called in the following cases:
     * - When access or refresh tokens have been updated externally
     * - When you want to clear sensitive token data (for example, during logout)
     *
     * Note: The result of `loadTokens` invocation is cached internally by default.
     * Calling this method will force the next authentication attempt to fetch fresh tokens
     * through the configured `loadTokens` function.
     *
     * If [BearerAuthConfig.cacheTokens] is set to `false`, this method has no effect
     * as tokens are not cached.
     *
     * Example usage:
     * ```kotlin
     * // Clear tokens on logout
     * client.authProvider<BearerAuthProvider>()?.clearToken()
     * // Or use the convenience extension
     * client.clearAuthTokens()
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.BearerAuthProvider.clearToken)
     */
    public override fun clearToken() {
        tokensHolder.clearToken()
    }
}
