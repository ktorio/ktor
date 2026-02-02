/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.auth.*
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Configuration for OpenID Connect discovery based JWT verification.
 * This class provides a convenient way to configure JWT authentication using
 * OpenID Connect discovery, which automatically fetches the JWKS (JSON Web Key Set)
 * endpoint from the authorization server's well-known configuration.
 *
 * Use this configuration with the [JWTAuthenticationProvider.Config.jwk] function to set up
 * automatic JWT verification with public keys from an OpenID Connect provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig)
 *
 * @see JWTAuthenticationProvider.Config.jwk
 * @see io.ktor.server.auth.fetchOpenIdConfiguration
 */
public class JwkConfig internal constructor() {
    internal data class CacheConfig(
        val size: Long,
        val expiresIn: Duration,
    ) {
        init {
            require(size > 0) { "cache maxEntries must be positive" }
            require(expiresIn.isPositive()) { "cache duration must be positive" }
        }
    }

    internal data class RateLimitConfig(
        val bucketSize: Long,
        val refillDuration: Duration,
    ) {
        init {
            require(bucketSize > 0) { "bucketSize must be positive" }
            require(refillDuration.isPositive()) { "rateLimit refillDuration must be positive" }
        }
    }

    /**
     * OpenID Connect configuration retrieved from the authorization server.
     * This can be set to the result of [io.ktor.server.auth.fetchOpenIdConfiguration].
     * The configuration contains the issuer URL and JWKS URI needed for token verification.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.openIdConfig)
     *
     * @see io.ktor.server.auth.fetchOpenIdConfiguration
     */
    public var openIdConfig: OpenIdConfiguration? = null

    /**
     * Expected audience claim value for JWT validation.
     * If set, only tokens with a matching `aud` claim will be accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.audience)
     */
    public var audience: String? = null

    /**
     * Additional JWT verification configuration.
     * Use this to add custom claims validation or other verification requirements.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.jwtConfigure)
     */
    public var jwtConfigure: JWTConfigureFunction = {}

    internal var cacheEnabled: Boolean = true
    internal var cacheConfig: CacheConfig? = null

    internal var rateLimitEnabled: Boolean = true
    internal var rateLimitConfig: RateLimitConfig? = null
    internal var jwkProviderFactory: ((String) -> JwkProvider)? = null

    /**
     * Configures caching for fetched JSON Web Keys.
     * Caching reduces the number of HTTP requests to the JWKS endpoint by storing previously fetched keys.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.cache)
     *
     * @param maxEntries Maximum number of keys to cache
     * @param duration How long cached keys remain valid before being refreshed
     */
    public fun cache(maxEntries: Long, duration: Duration) {
        cacheEnabled = true
        cacheConfig = CacheConfig(maxEntries, duration)
    }

    /**
     * Disables caching of JSON Web Keys.
     * When disabled, keys will be fetched from the JWKS endpoint for every token verification.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.disableCache)
     */
    public fun disableCache() {
        cacheEnabled = false
    }

    /**
     * Configures rate limiting for JWKS endpoint requests.
     * Rate limiting prevents excessive requests to the authorization server when unknown keys are encountered.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.rateLimit)
     *
     * @param bucketSize Maximum number of requests allowed in the time window
     * @param refillDuration Time window for the rate limit bucket
     */
    public fun rateLimit(bucketSize: Long, refillDuration: Duration) {
        rateLimitEnabled = true
        rateLimitConfig = RateLimitConfig(bucketSize, refillDuration)
    }

    /**
     * Disables rate limiting for JWKS endpoint requests.
     * Use with caution as this may result in excessive requests to the authorization server.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.disableRateLimit)
     */
    public fun disableRateLimit() {
        rateLimitEnabled = false
    }

    /**
     * Overrides the default JWK provider factory with a custom implementation.
     * This allows full control over how JSON Web Keys are fetched and cached.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.JwkConfig.jwkProviderFactory)
     *
     * @param factory Function that creates a [JwkProvider] from the JWKS URI
     */
    public fun jwkProviderFactory(factory: (String) -> JwkProvider) {
        jwkProviderFactory = factory
    }
}

internal fun JwkConfig.toJwkProvider(): JwkProvider {
    val jwksUri = requireNotNull(openIdConfig?.jwksUri) {
        "`JwkConfig.openIdConfig.jwksUri` is not specified"
    }
    jwkProviderFactory?.let { factory ->
        return factory(jwksUri)
    }
    val builder = JwkProviderBuilder(URI(jwksUri).toURL())
    when (cacheEnabled) {
        false -> builder.cached(false)
        else -> cacheConfig?.let {
            builder.cached(it.size, it.expiresIn.toJavaDuration())
        }
    }
    when (rateLimitEnabled) {
        false -> builder.rateLimited(false)
        else -> rateLimitConfig?.let {
            builder.rateLimited(
                it.bucketSize,
                it.refillDuration.inWholeMilliseconds,
                TimeUnit.MILLISECONDS
            )
        }
    }
    return builder.build()
}
