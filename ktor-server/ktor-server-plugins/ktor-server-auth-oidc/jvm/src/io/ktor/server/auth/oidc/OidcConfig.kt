/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
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
    /**
     * Optional HTTP client used for discovery and userinfo requests.
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
 * The provider is the typed root for route-facing capabilities. Bearer schemes and OAuth callbacks created from this
 * configuration expose the same principal type [P].
 *
 * @property name provider name used for generated routes and authentication scheme names.
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
    internal var oauthConfig: OidcOAuthConfig<P>? = null

    /**
     * Configures JWT verification shared by ID-token and JWT access-token validation.
     *
     * @param configure JWT verification configuration.
     */
    public fun jwt(configure: OidcJwtConfig.() -> Unit) {
        jwtConfig.apply(configure)
    }

    /**
     * Configures JWT verification for tests using [OpenIdTestKeys].
     *
     * This sets [OidcJwtConfig.jwkProviderFactory] to the in-memory public key provider and
     * [OidcJwtConfig.allowedAlgorithms] to the key algorithm. Use this with static [metadata] to avoid discovery and
     * JWKS HTTP calls while keeping normal JWT validation enabled.
     *
     * @param keys local test keys used to verify JWT signatures.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProviderConfig.jwt)
     */
    public fun jwt(keys: OpenIdTestKeys) {
        jwtConfig.jwkProviderFactory = { keys.jwkProvider }
        jwtConfig.allowedAlgorithms = setOf(keys.algorithm)
    }

    /**
     * Configures access-token acceptance for Bearer authentication and OAuth callbacks without an ID token.
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

    /**
     * Configures the OAuth/OpenID Connect login flow for this provider.
     *
     * This installs provider-specific login and callback routes. The callback verifies the token response and passes
     * [P] to the configured success handler.
     */
    public fun oauth(configure: OidcOAuthConfig<P>.() -> Unit = {}) {
        oauthConfig = (oauthConfig ?: OidcOAuthConfig(name)).apply(configure)
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
        oauthConfig?.validate(accessTokenAllowed = accessTokenConfig != null)
    }
}

/**
 * Maps a verified raw OpenID Connect principal to the route principal type [P].
 *
 * The transformer receives the current [RoutingContext] and verified principal.
 *
 * Return `null` to reject a verified ID token or JWT access token for this provider.
 *
 * @param P the principal type exposed to typed route handlers.
 */
public typealias PrincipalTransformer<P> = suspend RoutingContext.(OidcToken) -> P?

/**
 * JWT verification configuration shared by ID tokens and JWT access tokens.
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
     * When `null`, ID tokens use the provider discovery `id_token_signing_alg_values_supported` value when present.
     * JWT access tokens keep the default RSA/EC verification behavior unless this set is configured explicitly.
     *
     * `none` and HMAC algorithms are never accepted.
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
     * @param bucketSize the maximum number of requests allowed in the time window, defaults to 10.
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
 * Access-token authentication is disabled unless this block is configured. Unlike ID-token validation, access-token
 * audience never defaults to the OAuth client ID; configure resource audiences explicitly.
 *
 * @property audiences accepted resource identifiers for this server. Access tokens must include at least one value
 * from this set.
 * @property opaqueToken strategy used when the access token cannot be decoded as a JWT.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcAccessTokenConfig)
 */
@KtorDsl
public class OidcAccessTokenConfig internal constructor() {
    /**
     * Expected resource identifiers. Access tokens must include at least one of these audiences.
     */
    public var audiences: Set<String> = emptySet()

    /**
     * How opaque access tokens are handled.
     */
    public var opaqueToken: OpaqueTokenStrategy = OpaqueTokenStrategy.Reject

    internal fun validate() {
        require(audiences.isNotEmpty()) {
            "accessToken { audiences = ... } must be configured"
        }
    }
}

/**
 * Opaque access-token handling strategy.
 *
 * Opaque tokens are rejected by default. Configure [Introspect] to validate them with an RFC 7662 token
 * introspection endpoint.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpaqueTokenStrategy)
 */
public sealed class OpaqueTokenStrategy {
    /**
     * Reject opaque access tokens.
     */
    public object Reject : OpaqueTokenStrategy()

    /**
     * Introspect opaque access tokens with an RFC 7662 introspection endpoint.
     *
     * @property endpoint token introspection endpoint URL.
     * @property clientId client ID used to authenticate the resource server to the introspection endpoint.
     * @property clientSecret client secret used to authenticate the resource server to the introspection endpoint.
     * @property authMethod client authentication method used for introspection requests.
     */
    public class Introspect(
        public val endpoint: String,
        public val clientId: String,
        public val clientSecret: String,
        public val authMethod: OpaqueTokenIntrospectionAuthMethod =
            OpaqueTokenIntrospectionAuthMethod.ClientSecretBasic,
    ) : OpaqueTokenStrategy() {

        init {
            require(endpoint.isNotBlank()) {
                "opaqueToken introspection endpoint must be configured"
            }
            require(clientId.isNotBlank()) {
                "opaqueToken introspection clientId must be configured"
            }
            require(clientSecret.isNotBlank()) {
                "opaqueToken introspection clientSecret must be configured"
            }
        }
    }
}

/**
 * Client authentication methods supported for opaque-token introspection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpaqueTokenIntrospectionAuthMethod)
 */
public enum class OpaqueTokenIntrospectionAuthMethod {
    /**
     * Authenticate with HTTP Basic using the client ID and client secret.
     */
    ClientSecretBasic,

    /**
     * Authenticate by sending `client_id` and `client_secret` in the form body.
     */
    ClientSecretPost,
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

public sealed class CodeChallengeMethod {
    public abstract val name: String

    public object S256 : CodeChallengeMethod() {
        override val name: String = "S256"

        internal const val VERIFIER_LENGTH = 64
    }
}

/**
 * OAuth/OpenID Connect configuration.
 *
 * OAuth installs a provider-specific login route and callback route. The callback verifies the token response
 * and passes the transformed provider principal to [onSuccess].
 *
 * @param P the typed principal exposed to [onSuccess].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig)
 */
@KtorDsl
public class OidcOAuthConfig<P : Any> internal constructor(
    internal val providerName: String,
) {
    /**
     * OAuth client ID. Required when OAuth is configured.
     */
    public lateinit var clientId: String

    /**
     * OAuth client secret. Required when OAuth is configured.
     */
    public lateinit var clientSecret: String

    /**
     * OAuth scopes requested during authorization.
     *
     * The `openid` scope is required unless [OidcProviderConfig.accessToken] is configured, in which case
     * access-token-only OAuth callbacks may accept a response without an ID token.
     */
    public var scopes: List<String> = listOf("openid", "profile", "email")

    /**
     * Optional resource indicators added to authorization, token, and refresh requests.
     */
    public var resourceIndicators: List<String> = emptyList()

    /**
     * Expected audience for ID token validation in the callback flow.
     * Defaults to [clientId] when not specified.
     */
    public var idTokenAudience: String? = null

    /**
     * Enables userinfo request in the callback flow.
     */
    public var fetchUserInfo: Boolean = false

    /**
     * Symmetric key used to encrypt the in-flight OAuth state cookie carrying `state`, `nonce`, and the PKCE code
     * verifier between the login redirect and the callback.
     *
     * Required in production. In development mode an ephemeral key is generated when not set.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.stateEncryptionKey)
     */
    public var stateEncryptionKey: OidcStateEncryptionKey? = null

    internal var pkceEnabled: Boolean = true

    /**
     * Code challenge method used for PKCE (RFC 7636) during the authorization code flow.
     *
     * Only [CodeChallengeMethod.S256] is supported.
     *
     * When enabled, a per-request code verifier is generated and stored in the encrypted state cookie. The
     * authorization request adds the `code_challenge` (the Base64URL-encoded, unpadded SHA-256 digest of the
     * verifier) and `code_challenge_method` (`S256`) parameters, and the token exchange request adds the matching
     * `code_verifier` parameter so the provider can verify the challenge.
     *
     * Set to `null` to disable PKCE. Use this only with legacy OpenID Providers that reject PKCE parameters.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.codeChallengeMethod)
     */
    public var codeChallengeMethod: CodeChallengeMethod? = CodeChallengeMethod.S256

    /**
     * Configures the OAuth callback route URI.
     *
     * Defaults to `/oidc/{providerName}/callback`. Query parameters are not supported.
     */
    public var redirectUri: URLBuilder.() -> Unit = { path("oidc", providerName, "callback") }

    /**
     * Configures the OAuth login route URI.
     *
     * Defaults to `/oidc/{providerName}/login`. Query parameters are not supported.
     */
    public var loginUri: URLBuilder.() -> Unit = { path("oidc", providerName, "login") }

    /**
     * Called after a successful OAuth login.
     */
    internal var onSuccess: suspend RoutingContext.(P) -> Unit = { call.respond(HttpStatusCode.OK) }

    /**
     * Called when OAuth, OpenID Connect verification, or principal mapping fails during the callback.
     */
    internal var onFailure: UnauthorizedHandler = { call.respond(HttpStatusCode.Unauthorized) }

    /**
     * Disables PKCE (RFC 7636).
     *
     * Use only with legacy OpenID Providers that reject PKCE parameters.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.disablePkce)
     */
    public fun disablePkce() {
        pkceEnabled = false
    }

    /**
     * Sets the handler called after a successful OAuth/OIDC login.
     *
     * @param block handler invoked with the typed provider principal.
     */
    public fun onSuccess(block: suspend RoutingContext.(P) -> Unit) {
        onSuccess = block
    }

    /**
     * Sets the handler called when OIDC verification or principal mapping fails after token exchange.
     *
     * @param block failure handler.
     */
    public fun onFailure(block: UnauthorizedHandler) {
        onFailure = block
    }

    internal fun validate(accessTokenAllowed: Boolean) {
        require(::clientId.isInitialized) {
            "clientId must be configured"
        }
        require(::clientSecret.isInitialized) {
            "clientSecret must be configured"
        }
        require(accessTokenAllowed || "openid" in scopes) {
            "OAuth scopes for OpenID Connect must include openid unless accessToken { audiences = ... } is configured"
        }
        idTokenAudience?.let { audience ->
            require(audience.isNotBlank()) {
                "idTokenAudience must not be blank"
            }
        }
    }
}

internal fun oidcRoutePath(build: URLBuilder.() -> Unit): String {
    val url = URLBuilder().apply(build).build()
    require(url.encodedQuery.isEmpty()) {
        "$url must not include query parameters"
    }
    return url.encodedPath
}

internal fun ApplicationRequest.oidcRedirectUri(build: URLBuilder.() -> Unit): String {
    val requestOrigin = origin
    return URLBuilder().apply {
        protocol = URLProtocol.createOrDefault(requestOrigin.scheme)
        host = requestOrigin.serverHost
        port = requestOrigin.serverPort
        encodedPath = oidcRoutePath(build)
    }.buildString()
}
