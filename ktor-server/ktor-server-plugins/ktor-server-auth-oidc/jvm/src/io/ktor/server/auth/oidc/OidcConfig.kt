/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the [Oidc] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcPluginConfig)
 */
@KtorDsl
public class OidcPluginConfig {
    internal val environmentProviders: MutableMap<String, EnvConfig> = linkedMapOf()

    /**
     * Optional HTTP client used for OpenID Connect discovery requests.
     * If not configured, the plugin installs an internal client.
     */
    public var httpClient: HttpClient? = null

    /**
     * Discovery refresh interval after a successful application startup.
     * Set to `Duration.ZERO` to disable periodic refresh.
     */
    public var discoveryRefreshInterval: Duration = 15.minutes

    /**
     * Delay before the next periodic discovery refresh attempt after a failure.
     *
     * Successful refreshes use [discoveryRefreshInterval]. After a failed refresh, the next attempt uses this delay;
     * a later successful refresh resets the schedule back to [discoveryRefreshInterval].
     */
    public var discoveryRefreshFailureDelay: Duration = 1.minutes

    /**
     * Number of attempts for initial discovery during provider registration.
     *
     * Initial discovery blocks the suspend provider registration call until the provider has loaded metadata, or
     * until this number of attempts is exhausted. If discovery still fails after the final attempt, registration
     * fails with [OpenIdDiscoveryException].
     */
    public var initialDiscoveryAttempts: Int = 1

    /**
     * Delay between failed initial discovery attempts during provider registration.
     *
     * The delay is applied only between attempts. It is not used after the final failed attempt.
     */
    public var initialDiscoveryRetryDelay: Duration = 5.seconds

    /**
     * Represents a single configured OpenID Connect provider loaded from the environment.
     */
    @Serializable
    internal data class EnvConfig(
        val issuer: String,
    )

    internal fun Application.loadConfigFromEnvironment() {
        if (environment.config.propertyOrNull("ktor.openid") == null) {
            return
        }

        val root = environment.config.config("ktor.openid")
        root.keys().map { it.substringBefore(".") }.distinct().forEach { providerName ->
            this@OidcPluginConfig.environmentProviders[providerName] = root.property(providerName).getAs()
        }
    }

    internal fun validate() {
        require(initialDiscoveryAttempts >= 1) {
            "initialDiscoveryAttempts must be greater than or equal to 1"
        }
        require(initialDiscoveryRetryDelay.isFinite() && !initialDiscoveryRetryDelay.isNegative()) {
            "initialDiscoveryRetryDelay must be finite and non-negative"
        }
    }
}

/**
 * Configuration for a single OpenID Connect provider (issuer).
 *
 * The provider is the typed root for route-facing capabilities. Bearer schemes created from this configuration expose
 * the same principal type [P].
 *
 * @property name provider name used for generated authentication scheme names.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProviderConfig)
 */
@KtorDsl
public class OidcProviderConfig<P : Any> internal constructor(
    public val name: String,
    internal val principalType: KClass<P>,
    internal var principalTransformer: PrincipalTransformer<P>? = null
) {
    /**
     * Issuer URL. Used for OpenID Connect discovery (`<issuer>/.well-known/openid-configuration`) unless
     * [metadata] is configured.
     */
    public lateinit var issuer: String

    /**
     * Static OpenID Provider metadata for this provider.
     *
     * When configured, the provider skips initial discovery and disables periodic metadata refresh for this
     * provider.
     */
    public var metadata: OpenIdProviderMetadata? = null

    internal val jwtConfig: OidcJwtConfig = OidcJwtConfig()
    internal var accessTokenConfig: OidcAccessTokenConfig? = null
    internal var bearerConfig: OidcBearerConfig? = null

    internal val accessTokenAllowed: Boolean
        get() = accessTokenConfig != null

    /**
     * Configures JWT verification for JWT access tokens.
     */
    public fun jwt(configure: OidcJwtConfig.() -> Unit) {
        jwtConfig.apply(configure)
    }

    /**
     * Configures access-token acceptance for Bearer authentication.
     */
    public fun accessToken(configure: OidcAccessTokenConfig.() -> Unit) {
        accessTokenConfig = (accessTokenConfig ?: OidcAccessTokenConfig()).apply(configure)
    }

    /**
     * Enables Bearer token authentication and configures token extraction for this provider.
     *
     * Bearer authentication accepts access tokens only when [accessToken] is also configured with at least one
     * expected audience.
     */
    public fun bearer(configure: OidcBearerConfig.() -> Unit = {}) {
        bearerConfig = OidcBearerConfig().apply(configure)
    }

    internal fun validate() {
        require(::issuer.isInitialized && issuer.isNotBlank()) {
            "issuer must be configured"
        }
        metadata?.validate(expectedIssuer = issuer)
        require(bearerConfig == null || accessTokenConfig != null) {
            "Bearer authentication requires accessToken { audiences = ... }"
        }
        jwtConfig.validate()
        accessTokenConfig?.validate()
    }
}

/**
 * Maps a verified raw OpenID Connect principal to the route principal type [P].
 *
 * The transformer receives the current [RoutingContext] and verified principal.
 *
 * Return `null` to reject a verified JWT access token for this provider.
 *
 * @param P the principal type exposed to typed route handlers.
 */
public typealias PrincipalTransformer<P> = suspend RoutingContext.(OidcToken) -> P?

/**
 * JWT verification configuration.
 *
 * @property clockSkew accepted JWT clock skew.
 * @property allowedAlgorithms accepted JWT signing algorithms, or `null` to use provider defaults.
 * @property jwkProviderFactory custom JWK provider factory for JWT signature verification.
 * @property jwkBuilder additional customization for the default JWK provider builder.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcJwtConfig)
 */
@KtorDsl
public class OidcJwtConfig internal constructor() {
    internal class CacheConfig(
        val size: Long,
        val expiresIn: Duration,
    ) {
        init {
            require(size > 0) { "cache maxEntries must be positive" }
            require(expiresIn.isPositive()) { "cache duration must be positive" }
        }
    }

    internal class RateLimitConfig(
        val bucketSize: Long,
        val refillDuration: Duration,
    ) {
        init {
            require(bucketSize > 0) { "bucketSize must be positive" }
            require(refillDuration.isPositive()) { "rateLimit refillDuration must be positive" }
        }
    }

    /**
     * Accepted JWT clock skew in seconds.
     */
    public var clockSkew: Duration = 60.seconds

    /**
     * Accepted JWT signing algorithms.
     *
     * When `null`, JWT access tokens keep the default RSA/EC verification behavior. `none` and HMAC algorithms are
     * never accepted.
     */
    public var allowedAlgorithms: Set<SignatureAlgorithm>? = null

    /**
     * Customize JWK provider creation for JWT signature verification.
     *
     * A custom provider factory owns JWK fetching, caching, and rate limiting. It cannot be combined with
     * [jwkCache], [disableJwkCache], [jwkRateLimit], or [disableJwkRateLimit].
     */
    public var jwkProviderFactory: ((String) -> JwkProvider)? = null

    /**
     * Additional JWK provider builder customization for JWT signature verification.
     *
     * This low-level hook is applied after [jwkCache], [disableJwkCache], [jwkRateLimit], and [disableJwkRateLimit],
     * so it can still override the final [JwkProviderBuilder] behavior.
     */
    public var jwkBuilder: JwkProviderBuilder.() -> Unit = {}

    internal var jwkCacheEnabled: Boolean = true
    internal var jwkCacheConfig: CacheConfig? = null
    internal var jwkCacheConfigured: Boolean = false

    internal var jwkRateLimitEnabled: Boolean = true
    internal var jwkRateLimitConfig: RateLimitConfig? = null
    internal var jwkRateLimitConfigured: Boolean = false

    /**
     * Configures caching for fetched JSON Web Keys.
     *
     * @param maxEntries maximum number of keys to cache, defaults to 5.
     * @param duration how long cached keys remain valid before being refreshed, defaults to 10 hours.
     */
    public fun jwkCache(maxEntries: Long = 5, duration: Duration = 10.hours) {
        jwkCacheEnabled = true
        jwkCacheConfig = CacheConfig(maxEntries, duration)
        jwkCacheConfigured = true
    }

    /**
     * Disables caching of JSON Web Keys.
     */
    public fun disableJwkCache() {
        jwkCacheEnabled = false
        jwkCacheConfigured = true
    }

    /**
     * Configures rate limiting for JWKS endpoint requests.
     *
     * @param bucketSize maximum number of requests allowed in the time window, defaults to 10.
     * @param refillDuration time window for the rate limit bucket, defaults to 1 minute.
     */
    public fun jwkRateLimit(bucketSize: Long = 10, refillDuration: Duration = 1.minutes) {
        jwkRateLimitEnabled = true
        jwkRateLimitConfig = RateLimitConfig(bucketSize, refillDuration)
        jwkRateLimitConfigured = true
    }

    /**
     * Disables rate limiting for JWKS endpoint requests.
     */
    public fun disableJwkRateLimit() {
        jwkRateLimitEnabled = false
        jwkRateLimitConfigured = true
    }

    internal fun validate() {
        require(jwkProviderFactory == null || (!jwkCacheConfigured && !jwkRateLimitConfigured)) {
            "jwt { jwkProviderFactory = ... } cannot be combined with jwkCache or jwkRateLimit configuration"
        }
        allowedAlgorithms?.forEach { algorithm ->
            require(algorithm.jwaName != null) {
                "jwt { allowedAlgorithms = ... } supports only RSA and EC JWA signature algorithms"
            }
        }
    }
}

/**
 * Access-token verification policy.
 *
 * Access-token authentication is disabled unless this block is configured. Configure resource audiences explicitly.
 *
 * @property audiences accepted resource identifiers for this server. Access tokens must include at least one value
 * from this set.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcAccessTokenConfig)
 */
@KtorDsl
public class OidcAccessTokenConfig internal constructor() {
    /**
     * Expected resource identifiers. Access tokens must include at least one of these audiences.
     */
    public var audiences: Set<String> = emptySet()

    internal fun validate() {
        require(audiences.isNotEmpty()) {
            "accessToken { audiences = ... } must be configured"
        }
    }
}

/**
 * Extracts a Bearer token candidate from an application call.
 *
 * Return `null` when this source does not contain a token.
 */
public typealias TokenExtractor = (ApplicationCall) -> String?

/**
 * Bearer token extraction configuration for a discovered issuer.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcBearerConfig)
 */
@KtorDsl
public class OidcBearerConfig internal constructor() {
    /**
     * Custom token extractor for Bearer authentication.
     *
     * When `null`, the provider reads the standard `Authorization: Bearer <token>` header.
     */
    public var tokenExtractor: TokenExtractor? = null
}
