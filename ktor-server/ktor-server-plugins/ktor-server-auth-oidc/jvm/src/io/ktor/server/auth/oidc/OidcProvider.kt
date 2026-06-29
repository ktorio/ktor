/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

private val TokenRefreshResultTtl = 1.seconds
private const val TokenRefreshCacheMaxSize = 1024
private val TokenRefreshCacheEvictor = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "ktor-oidc-token-refresh-cache").apply {
        isDaemon = true
    }
}

/**
 * Typed authentication capabilities for one configured OpenID Connect provider.
 *
 * [bearer] is available when the provider was configured with `bearer { }`.
 * [sessions] is available when the provider was configured with `sessions { }`.
 *
 * @param P principal type exposed by this provider's route-facing capabilities.
 * @property name provider name. It is also used to derive default routes (`/oidc/{name}/...`), the OAuth scheme
 * name (`{name}-oauth`), the Bearer scheme name (`{name}-bearer`), and the default session cookie root
 * (`{NAME}_SESSION`).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProvider)
 */
public class OidcProvider<P : Any> internal constructor(
    public val name: String,
    internal val client: HttpClient,
    internal val config: OidcProviderConfig<P>,
    internal val developmentMode: Boolean = true
) {
    public val issuer: String = config.issuer

    internal val principalType: KClass<P> = config.principalType

    internal val jwtConfig: OidcJwtConfig
        get() = checkNotNull(config.jwtConfig) { "JWT is not enabled for provider $name" }

    internal val oauthConfig: OidcOAuthConfig<P>
        get() = checkNotNull(config.oauthConfig) { "OAuth is not enabled for provider $name" }

    internal val sessionConfig: OidcSessionConfig<P>
        get() = checkNotNull(config.sessionConfig) {
            "Sessions are not enabled. Call sessions { } in the provider $name."
        }

    internal val accessTokenConfig: OidcAccessTokenConfig
        get() = checkNotNull(config.accessTokenConfig) { "Access token is not enabled for provider $name" }

    internal var resourceMetadataUrl: String? = null

    internal val bearerConfig: OidcBearerConfig
        get() = checkNotNull(config.bearerConfig) {
            "Bearer scheme is not enabled. Call bearer { } in the provider $name."
        }

    internal val logger: Logger = LoggerFactory.getLogger("io.ktor.server.auth.oidc.OidcProvider[$name]")

    @Volatile
    private var providerState: OidcProviderState? = null

    internal val oauthFlow by lazy { createOauthFlow() }
    internal val oauthSessionFlow by lazy { createSessions(secure = !developmentMode) }

    internal val stateCodec: OidcStateCodec by lazy { createStateCodec() }

    internal val sessionRefreshPath: String by lazy { oidcRoutePath(sessionConfig.refreshUri) }

    internal val sessionLogoutPath: String by lazy { oidcRoutePath(sessionConfig.logoutUri) }

    private val tokenRefreshes = ConcurrentHashMap<String, CompletableDeferred<OidcTokenRefreshResult>>()

    internal val canIntrospectOpaqueToken: Boolean =
        config.accessTokenConfig?.opaqueToken is OpaqueTokenStrategy.Introspect

    internal fun updateMetadata(newMetadata: OpenIdProviderMetadata) {
        val currentState = providerState
        val nextJwkProvider = if (currentState?.metadata?.jwksUri == newMetadata.jwksUri) {
            currentState.jwkProvider
        } else {
            computeJwkProvider(newMetadata.jwksUri)
        }
        providerState = OidcProviderState(newMetadata, nextJwkProvider)
    }

    /**
     * Returns the currently active OpenID Connect discovery metadata for this provider.
     * The returned value can change after a successful periodic discovery refresh.
     *
     * @throws IllegalStateException when metadata has not been initialized yet.
     */
    public fun currentMetadata(): OpenIdProviderMetadata =
        checkNotNull(providerState) {
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
        checkNotNull(providerState) {
            "JWK provider is not initialized for OpenID Connect provider $name"
        }.jwkProvider

    context(ctx: RoutingContext)
    internal suspend fun transformPrincipal(token: OidcToken): P? {
        config.principalTransformer?.let { transform ->
            return ctx.transform(token)
        }
        check(principalType.isInstance(token)) {
            "Invalid principal type. Returned principal is an instance of ${token::class}"
        }
        @Suppress("UNCHECKED_CAST")
        return token as P
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
                builder.rateLimited(
                    it.bucketSize,
                    it.refillDuration.inWholeMilliseconds,
                    TimeUnit.MILLISECONDS
                )
            }
        }
        return builder.apply(jwtConfig.jwkBuilder).build()
    }

    private fun createStateCodec(): OidcStateCodec {
        val encryptionKey = checkNotNull(oauthConfig.stateEncryptionKey)
        return OidcStateCodec(encryptionKey)
    }

    /**
     * Refreshes token material for this provider using the supplied refresh token.
     *
     * @param refreshToken Refresh token to send to the provider token endpoint.
     * @return Raw token response fields and an optional verified ID-token principal.
     * @throws IllegalArgumentException when OAuth is not enabled.
     */
    public suspend fun refreshToken(refreshToken: String): OidcTokenRefreshResult {
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
            pending.completeExceptionally(cause)
            tokenRefreshes.remove(refreshToken, pending)
            throw cause
        }
    }

    private fun scheduleTokenRefreshEviction(
        refreshToken: String,
        pending: CompletableDeferred<OidcTokenRefreshResult>
    ) {
        TokenRefreshCacheEvictor.schedule(
            { tokenRefreshes.remove(refreshToken, pending) },
            TokenRefreshResultTtl.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )
        pruneCompletedTokenRefreshes()
    }

    private fun pruneCompletedTokenRefreshes() {
        val excess = tokenRefreshes.size - TokenRefreshCacheMaxSize
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
     * Builds an RP-initiated logout URL for the provider.
     *
     * Local plugin-managed logout clears the local session before building this URL, so a failure to build or reach
     * the provider logout URL does not restore the local session.
     *
     * @param idTokenHint ID token hint to pass to the provider logout endpoint.
     * @param postLogoutRedirectUri Optional absolute URI to receive the user after the provider logout.
     * @return Provider logout URL.
     * @throws IllegalArgumentException when [idTokenHint] is blank or metadata does not expose an end-session endpoint.
     */
    public fun buildLogoutUrl(idTokenHint: String, postLogoutRedirectUri: String?): String =
        buildLogoutUrlInternal(idTokenHint, postLogoutRedirectUri)

    /**
     * Typed Bearer authentication scheme.
     *
     * Use with `authenticateWith(provider.bearer)`.
     *
     * @throws IllegalStateException when the provider was not configured with `bearer { }`.
     */
    public val bearer: DefaultAuthScheme<P, AuthenticatedContext<P>> by lazy {
        createBearerScheme(resourceMetadataUrl)
    }

    /**
     * Typed browser session authentication scheme.
     *
     * OpenID Connect stores the raw [OidcToken.Id] in a provider-specific session, then maps that value
     * to [P] for routes protected with `authenticateWith(provider.sessions)`.
     *
     * @throws IllegalStateException when the provider was not configured with `sessions { }`.
     */
    public val sessions: SessionAuthScheme<OidcToken.Id, P, SessionAuthenticatedContext<OidcToken.Id, P>>
        get() = oauthSessionFlow.sessions
}

private class OidcProviderState(
    val metadata: OpenIdProviderMetadata,
    val jwkProvider: JwkProvider,
)
