/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

private val TokenRefreshCacheEvictor = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "ktor-oidc-token-refresh-cache").apply {
        isDaemon = true
    }
}

/**
 * Typed authentication capabilities for one configured OpenID Connect provider.
 *
 * Exposes verified protocol-native authentication schemes with precise [OidcToken] principal types.
 * Map those schemes to application principals with [io.ktor.server.auth.mapPrincipal].
 *
 * [jwtBearer] is available when the provider was configured with `bearer { }`.
 * [introspectionBearer] is available when nested `bearer { introspection { } }` is configured.
 * [session] is available when the provider was configured with `oauth { }` and sessions were not disabled.
 *
 * @property name provider name. It is also used to derive default routes (`/oidc/{name}/...`), the OAuth scheme
 * name (`{name}-oauth`), Bearer scheme names (`{name}-jwt-bearer`, `{name}-introspection-bearer`), and the default
 * session cookie root (`{NAME}_SESSION`).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProvider)
 */
@ExperimentalKtorApi
public class OidcProvider internal constructor(
    public val name: String,
    internal val client: HttpClient,
    internal val config: OidcProviderConfig,
    internal val developmentMode: Boolean = true
) {
    /**
     * Configured issuer identifier URL for this provider.
     *
     * Used for OpenID Connect discovery (`<issuer>/.well-known/openid-configuration`) unless static
     * [OidcProviderConfig.metadata] was supplied, and as the expected `iss` claim when verifying tokens.
     */
    public val issuer: String = config.issuer

    /**
     * Returns the currently active OpenID Connect discovery metadata for this provider.
     * The returned value can change after a successful periodic discovery refresh.
     *
     * @throws IllegalStateException when metadata has not been initialized yet.
     */
    public fun currentMetadata(): OpenIdProviderMetadata =
        checkNotNull(state) {
            "OpenID Connect metadata is not initialized for provider $name"
        }.metadata

    /**
     * Returns the currently active JWK provider for this provider.
     * The returned value can change after a successful periodic discovery refresh when the discovery document points
     * to a different JWKS URI.
     *
     * @throws IllegalStateException when metadata has not been initialized yet.
     */
    public fun currentJwkProvider(): JwkProvider =
        checkNotNull(state) {
            "JWK provider is not initialized for OpenID Connect provider $name"
        }.jwkProvider

    internal inline fun <R> withCapturedState(block: (context(State) () -> R)): R {
        val currentState = checkNotNull(state) {
            "JWK provider is not initialized for OpenID Connect provider $name"
        }
        return block.invoke(currentState)
    }

    /**
     * Refreshes token material for this provider using the supplied refresh token.
     *
     * Concurrent callers with the same refresh token share one token-endpoint request. After success, the result
     * remains available for [OidcProviderConfig.tokenRefreshCacheTtl] so stragglers reuse it.
     * [Duration.ZERO] coalesces in-flight work only.
     *
     * @param refreshToken Refresh token to send to the provider token endpoint.
     * @return Raw token response fields and an optional verified ID-token principal.
     * @throws IllegalStateException when OAuth is not enabled.
     * @throws OidcTokenRejectedException when tokens in the refresh response fail validation.
     * @throws io.ktor.client.plugins.ResponseException when the provider rejects the request, for example,
     * with an `invalid_grant` error response.
     */
    public suspend fun refreshToken(refreshToken: String): OidcTokenRefreshResult = withCapturedState {
        pruneCompletedTokenRefreshes()

        var existing = true
        val pending = tokenRefreshes.computeIfAbsent(refreshToken) {
            existing = false
            CompletableDeferred()
        }

        if (existing) {
            return pending.await()
        }

        try {
            val result = refreshTokenInternal(refreshToken)
            pending.complete(result)
            scheduleTokenRefreshEviction(refreshToken, pending)
            return result
        } catch (cause: Throwable) {
            pending.completeExceptionally(exception = cause)
            tokenRefreshes.remove(key = refreshToken, value = pending)
            throw cause
        }
    }

    /**
     * JWT Bearer authentication scheme.
     *
     * Accepts only locally verified JWT access tokens.
     * Use with `authenticateWith(auth0.jwtBearer)` after [Oidc.identityProvider].
     *
     * @throws IllegalStateException when the provider was not configured with `bearer { }`.
     */
    public val jwtBearer: SimpleAuthenticationScheme<OidcToken.Access> by lazy {
        createJwtBearerScheme()
    }

    /**
     * Introspection Bearer authentication scheme.
     *
     * Sends any presented access token to RFC 7662 introspection, whether JWT-formatted or opaque.
     * Use with `authenticateWith(auth0.introspectionBearer)` after [Oidc.identityProvider].
     *
     * @throws IllegalStateException when the provider was not configured with `bearer { introspection { } }`.
     */
    public val introspectionBearer: SimpleAuthenticationScheme<OidcToken.Introspected> by lazy {
        check(canIntrospect) {
            "Introspection Bearer is not enabled. Call introspection { } inside bearer { } for provider $name."
        }
        createIntrospectionBearerScheme(resourceMetadataUrl = resourceMetadataUrl)
    }

    /**
     * Typed browser session authentication scheme.
     *
     * OpenID Connect stores the raw [OidcToken.Id] in a provider-specific session. Map it to an application
     * principal with [io.ktor.server.auth.mapPrincipal] when protecting routes.
     *
     * @throws IllegalStateException when OAuth sessions are not enabled (`oauth { }` was omitted or
     * [OidcOAuthConfig.disableSessions] was called).
     */
    public val session: SessionAuthenticationScheme<OidcToken.Id, OidcToken.Id>
        get() = oauthSessionFlow.session

    internal val jwtConfig: OidcJwtConfig
        get() = config.jwtConfig

    internal val oauthConfig: OidcOAuthConfig
        get() = checkNotNull(config.oauthConfig) { "OAuth is not enabled for provider $name" }

    internal val sessionConfig: OidcSessionsConfig
        get() = checkNotNull(oauthConfig.sessionConfig) {
            "Sessions are not enabled. Call sessions { } inside oauth { }, or omit disableSessions(), for provider $name."
        }

    internal var resourceMetadataUrl: String? = null

    internal val bearerConfig: OidcBearerConfig
        get() = checkNotNull(config.bearerConfig) {
            "Bearer scheme is not enabled. Call bearer { audience = ... } in the provider $name."
        }

    internal val introspectionConfig: OidcTokenIntrospectionConfig
        get() = checkNotNull(bearerConfig.introspectionConfig) {
            "Introspection Bearer is not enabled. Call introspection { } inside bearer { } for provider $name."
        }

    internal val logger: Logger = LoggerFactory.getLogger("io.ktor.server.auth.oidc.OidcProvider[$name]")

    @Volatile
    private var state: State? = null

    internal val oauthFlow by lazy { createOauthFlow() }
    internal val oauthSessionFlow by lazy { createOAuthSession(secureCookie = !developmentMode) }

    internal val stateCodec: OidcStateCodec by lazy { createStateCodec() }

    internal val stateCookieName: String = oidcStateCookieName(name)

    internal val tokenRefreshes = ConcurrentHashMap<String, CompletableDeferred<OidcTokenRefreshResult>>()

    internal val canIntrospect: Boolean = config.bearerConfig?.introspectionConfig != null

    internal fun updateMetadata(newMetadata: OpenIdProviderMetadata) {
        val currentState = state
        val nextJwkProvider = if (currentState?.metadata?.jwksUri == newMetadata.jwksUri) {
            currentState.jwkProvider
        } else {
            computeJwkProvider(newMetadata.jwksUri)
        }
        state = State(newMetadata, nextJwkProvider)
    }

    private fun computeJwkProvider(jwksUri: String): JwkProvider {
        val factory = jwtConfig.jwkProviderFactory
        if (factory != null) {
            return factory(jwksUri)
        }
        val jwksUrl = URI(jwksUri).toURL()
        val builder = JwkProviderBuilder(jwksUrl)
        when (jwtConfig.jwkCacheEnabled) {
            false -> builder.cached(false)

            else -> jwtConfig.jwkCacheConfig?.let {
                builder.cached(it.size, it.expiresIn.toJavaDuration())
            }
        }
        when (jwtConfig.jwkRateLimitEnabled) {
            false -> builder.rateLimited(false)

            else -> jwtConfig.jwkRateLimitConfig?.let {
                val refillRate = it.refillDuration.inWholeMilliseconds
                val refillUnit = TimeUnit.MILLISECONDS
                builder.rateLimited(it.bucketSize, refillRate, refillUnit)
            }
        }
        return builder.apply(jwtConfig.jwkBuilder).build()
    }

    private fun createStateCodec(): OidcStateCodec {
        val encryptionKey = checkNotNull(oauthConfig.stateEncryptionKey)
        return OidcStateCodec(encryptionKey)
    }

    private fun scheduleTokenRefreshEviction(
        refreshToken: String,
        pending: CompletableDeferred<OidcTokenRefreshResult>
    ) {
        val ttl = config.tokenRefreshCacheTtl
        val command = { tokenRefreshes.remove(key = refreshToken, value = pending) }
        if (ttl == Duration.ZERO) {
            command()
        } else {
            TokenRefreshCacheEvictor.schedule(command, ttl.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
        pruneCompletedTokenRefreshes()
    }

    private fun pruneCompletedTokenRefreshes() {
        val excess = tokenRefreshes.size - config.tokenRefreshCacheMaxSize
        if (excess <= 0) {
            return
        }

        var removed = 0
        val entries = tokenRefreshes.entries.iterator()
        while (entries.hasNext() && removed < excess) {
            val entry = entries.next()
            if (entry.value.isCompleted) {
                entries.remove()
                removed++
            }
        }
    }

    /**
     * Builds an RP-initiated logout URL.
     *
     * @param idTokenHint ID token hint to pass to the provider logout endpoint.
     * @param postLogoutRedirectUri Optional absolute URI to receive the user after the provider logout.
     * @throws IllegalArgumentException when [idTokenHint] is blank or metadata does not expose an end-session endpoint.
     */
    internal fun buildLogoutUrl(idTokenHint: String, postLogoutRedirectUri: String?): String {
        require(idTokenHint.isNotBlank()) {
            "idTokenHint must not be blank for provider '$name'"
        }
        val endSessionEndpoint = requireNotNull(currentMetadata().endSessionEndpoint) {
            "RP-Initiated logout is not supported by provider '$name'"
        }
        return URLBuilder(endSessionEndpoint).apply {
            parameters.append("id_token_hint", idTokenHint)
            config.oauthConfig?.clientId?.let { clientId ->
                parameters.append("client_id", clientId)
            }
            postLogoutRedirectUri?.let { uri ->
                parameters.append("post_logout_redirect_uri", uri)
            }
        }.buildString()
    }

    internal class State(
        val metadata: OpenIdProviderMetadata,
        val jwkProvider: JwkProvider,
    )
}
