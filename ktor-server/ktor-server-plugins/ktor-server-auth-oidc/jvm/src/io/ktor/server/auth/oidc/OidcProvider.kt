/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.toJavaDuration

/**
 * Typed authentication capabilities for one configured OpenID Connect provider.
 *
 * [bearer] is available when the provider was configured with `bearer { }`.
 *
 * @param P principal type exposed by this provider's route-facing capabilities.
 * @property name provider name. It is also used to derive the Bearer scheme name (`{name}-bearer`).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProvider)
 */
public class OidcProvider<P : Any> internal constructor(
    public val name: String,
    internal val client: HttpClient,
    internal val config: OidcProviderConfig<P>,
) {
    public val issuer: String = config.issuer

    internal val principalType: KClass<P> = config.principalType

    internal val jwtConfig: OidcJwtConfig
        get() = checkNotNull(config.jwtConfig) { "JWT is not enabled for provider $name" }

    internal val accessTokenConfig: OidcAccessTokenConfig
        get() = checkNotNull(config.accessTokenConfig) { "Access token is not enabled for provider $name" }

    internal val bearerConfig: OidcBearerConfig
        get() = checkNotNull(config.bearerConfig) {
            "Bearer scheme is not enabled. Call bearer { } in the provider $name."
        }

    internal val logger: Logger = LoggerFactory.getLogger("io.ktor.server.auth.oidc.OidcProvider[$name]")

    @Volatile
    private var providerState: OidcProviderState? = null

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
    internal suspend fun transformPrincipal(principal: OidcToken): P? {
        config.principalTransformer?.let { transform ->
            return ctx.transform(principal)
        }
        check(principalType.isInstance(principal)) {
            "Invalid principal type. Returned principal is an instance of ${principal::class}"
        }
        @Suppress("UNCHECKED_CAST")
        return principal as P
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

    /**
     * Typed Bearer authentication scheme.
     *
     * Use with `authenticateWith(provider.bearer)`.
     *
     * @throws IllegalStateException when the provider was not configured with `bearer { }`.
     */
    public val bearer: OidcBearerScheme<P> by lazy { createBearerScheme() }
}

private data class OidcProviderState(
    val metadata: OpenIdProviderMetadata,
    val jwkProvider: JwkProvider,
)
