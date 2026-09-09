/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.annotations.*
import io.ktor.utils.io.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the [Oidc] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcPluginConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcPluginConfig {
    internal var protectedResourceConfig: ProtectedResourceMetadataConfig? = null

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
     * fails with [OidcDiscoveryException].
     */
    public var initialDiscoveryAttempts: Int = 1

    /**
     * Delay between failed initial discovery attempts during provider registration.
     *
     * The delay is applied only between attempts. It is not used after the final failed attempt.
     */
    public var initialDiscoveryRetryDelay: Duration = 5.seconds

    /**
     * Configures OAuth 2.0 Protected Resource Metadata (RFC 9728).
     *
     * When configured, the plugin serves a `/.well-known/oauth-protected-resource` endpoint with
     * metadata for this resource and includes a `resource_metadata` parameter in `WWW-Authenticate`
     * headers on Bearer authentication failures.
     */
    public fun protectedResource(resource: String, configure: ProtectedResourceMetadataConfig.() -> Unit = {}) {
        protectedResourceConfig = ProtectedResourceMetadataConfig(resource).apply(configure)
    }

    internal fun validate() {
        require(initialDiscoveryAttempts >= 1) {
            "initialDiscoveryAttempts must be greater than or equal to 1"
        }
        require(initialDiscoveryRetryDelay.isFinite() && !initialDiscoveryRetryDelay.isNegative()) {
            "initialDiscoveryRetryDelay must be finite and non-negative"
        }
        require(discoveryRefreshInterval.isFinite() && !discoveryRefreshInterval.isNegative()) {
            "discoveryRefreshInterval must be finite and non-negative. Use Duration.ZERO to disable periodic refresh"
        }
        require(discoveryRefreshFailureDelay.isFinite() && discoveryRefreshFailureDelay.isPositive()) {
            "discoveryRefreshFailureDelay must be finite and positive"
        }
    }
}

/**
 * Configuration for a single OpenID Connect provider (issuer).
 *
 * The provider shares discovery, JWT, Bearer, and OAuth configuration. Route-facing schemes expose precise
 * [OidcToken] subtypes on [OidcProvider]. Map those schemes to application principals with
 * [io.ktor.server.auth.mapPrincipal].
 *
 * @property name provider name used for generated routes and authentication scheme names.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProviderConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcProviderConfig internal constructor(
    public val name: String
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
     * Enables resource-server Bearer authentication for this provider.
     *
     * Configuring [bearer] enables JWT Bearer ([OidcProvider.jwtBearer]) and requires non-empty
     * [OidcBearerConfig.audience]. Nested [OidcBearerConfig.introspection] additionally enables
     * introspection Bearer.
     */
    public fun bearer(configure: OidcBearerConfig.() -> Unit = {}) {
        bearerConfig = OidcBearerConfig().apply(configure)
    }

    /**
     * Configures the OAuth/OpenID Connect login flow for this provider.
     *
     * This installs provider-specific login and callback routes. The callback requires an ID token and the `openid`
     * scope. Browser sessions are enabled by default; customize them with [OidcOAuthConfig.sessions] or opt out with
     * [OidcOAuthConfig.disableSessions].
     */
    public fun oauth(configure: OidcOAuthConfig.() -> Unit) {
        val config = OidcOAuthConfig(name).apply(configure)
        if (!config.sessionsDisabled && config.sessionConfig == null) {
            config.sessions()
        }
        oauthConfig = config
    }

    /**
     * How long a completed [OidcProvider.refreshToken] result stays available so concurrent callers with the same
     * refresh token reuse it.
     *
     * This is a single-flight share window, not session storage. The default is one second.
     * [Duration.ZERO] coalesces in-flight refreshes only and removes the completed entry immediately.
     */
    public var tokenRefreshCacheTtl: Duration = 1.seconds

    /**
     * Maximum number of in-flight or recently completed [OidcProvider.refreshToken] entries retained for this
     * provider.
     *
     * When the map exceeds this size, completed entries are pruned. The default is 1024.
     */
    public var tokenRefreshCacheMaxSize: Int = 1024

    internal val jwtConfig: OidcJwtConfig = OidcJwtConfig()
    internal var bearerConfig: OidcBearerConfig? = null
    internal var oauthConfig: OidcOAuthConfig? = null

    internal fun validate() {
        require(::issuer.isInitialized && issuer.isNotBlank()) {
            "'issuer' must be configured"
        }
        require(tokenRefreshCacheTtl.isFinite() && !tokenRefreshCacheTtl.isNegative()) {
            "tokenRefreshCacheTtl must be finite and non-negative"
        }
        require(tokenRefreshCacheMaxSize >= 1) {
            "tokenRefreshCacheMaxSize must be greater than or equal to 1"
        }
        metadata?.validate(expectedIssuer = issuer)
        jwtConfig.validate()
        bearerConfig?.validate()
        oauthConfig?.validate()
    }
}

/**
 * Extracts a Bearer token candidate from an application call.
 *
 * Return `null` when this source does not contain a token.
 */
public typealias OidcTokenExtractor = RoutingContext.() -> String?

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
@ExperimentalKtorApi
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

    internal var jwkCacheEnabled: Boolean = true
    internal var jwkCacheConfig: CacheConfig? = null
    internal var jwkCacheConfigured: Boolean = false

    internal var jwkRateLimitEnabled: Boolean = true
    internal var jwkRateLimitConfig: RateLimitConfig? = null
    internal var jwkRateLimitConfigured: Boolean = false

    internal fun validate() {
        require(clockSkew.isFinite() && !clockSkew.isNegative()) {
            "clockSkew must be finite and non-negative"
        }
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
 * Access-token verification policy for resource-server Bearer authentication.
 *
 * Configuring [OidcProviderConfig.bearer] enables JWT Bearer authentication and requires non-empty [audience].
 * Nested [introspection] additionally enables introspection Bearer authentication for opaque or JWT-formatted tokens.
 *
 * Bearer audiences are resource identifiers for this server. They are independent of OAuth [OidcOAuthConfig.clientId],
 * which is used as the ID-token audience for login callbacks. If a Bearer audience equals the OAuth client ID, the
 * plugin logs a warning: a valid ID token can satisfy signature, issuer, and audience checks. JWT Bearer still rejects
 * tokens whose `token_use` or `typ` identifies an ID token.
 *
 * @property audience accepted resource identifiers. Access tokens must include at least one value from this set.
 * @property tokenExtractor custom token extractor shared by JWT and introspection Bearer schemes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcBearerConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcBearerConfig internal constructor() {
    /**
     * Expected resource identifiers. Access tokens must include at least one of these audiences.
     */
    public var audience: Set<String> = emptySet()

    /**
     * Custom token extractor for Bearer authentication.
     *
     * When `null`, the provider reads the standard `Authorization: Bearer <token>` header.
     * Shared by JWT Bearer and introspection Bearer schemes.
     */
    public var tokenExtractor: OidcTokenExtractor? = null

    /**
     * Optional RFC 7662 token introspection configuration.
     *
     * When configured, enables introspection Bearer authentication in addition to JWT Bearer.
     */
    public fun introspection(configure: OidcTokenIntrospectionConfig.() -> Unit) {
        introspectionConfig = OidcTokenIntrospectionConfig().apply(configure)
    }

    internal var introspectionConfig: OidcTokenIntrospectionConfig? = null

    internal fun validate() {
        require(audience.isNotEmpty()) {
            "bearer { audience = ... } must be configured with at least one audience"
        }
        introspectionConfig?.validate()
    }
}

/**
 * RFC 7662 token introspection configuration for Bearer authentication.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcTokenIntrospectionConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcTokenIntrospectionConfig internal constructor() {
    /**
     * Token introspection endpoint URL.
     */
    public lateinit var endpoint: String

    /**
     * Client ID used to authenticate the resource server to the introspection endpoint.
     */
    public lateinit var clientId: String

    /**
     * Client secret used to authenticate the resource server to the introspection endpoint.
     */
    public lateinit var clientSecret: String

    /**
     * Client authentication method used for introspection requests.
     */
    public var authMethod: ClientAuthenticationMethod = ClientAuthenticationMethod.ClientSecretBasic

    internal fun validate() {
        require(::endpoint.isInitialized && endpoint.isNotBlank()) {
            "introspection { endpoint = ... } must be configured"
        }
        require(::clientId.isInitialized && clientId.isNotBlank()) {
            "introspection { clientId = ... } must be configured"
        }
        require(::clientSecret.isInitialized && clientSecret.isNotBlank()) {
            "introspection { clientSecret = ... } must be configured"
        }
    }
}

/**
 * Client authentication methods for provider endpoints that require client credentials, such as the token
 * endpoint and the token introspection endpoint.
 *
 * Values correspond to the IANA "OAuth Token Endpoint Authentication Methods" registry, which both
 * `token_endpoint_auth_methods_supported` and `introspection_endpoint_auth_methods_supported` metadata use.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.ClientAuthenticationMethod)
 */
@ExperimentalKtorApi
@SubclassOptInRequired(InternalKtorSubclassing::class)
public interface ClientAuthenticationMethod {
    /**
     * Authenticate with HTTP Basic using the client ID and client secret.
     */
    public object ClientSecretBasic : ClientAuthenticationMethod

    /**
     * Authenticate by sending `client_id` and `client_secret` in the form body.
     */
    public object ClientSecretPost : ClientAuthenticationMethod
}

@ExperimentalKtorApi
@SubclassOptInRequired(InternalKtorSubclassing::class)
public interface CodeChallengeMethod {
    public val name: String

    public object S256 : CodeChallengeMethod {
        override val name: String = "S256"

        internal const val VERIFIER_LENGTH = 64
    }
}

internal typealias OidcOAuthAuthenticatedHandler = suspend RoutingContext.(OidcToken.Id) -> Unit

/**
 * OAuth/OpenID Connect configuration.
 *
 * OAuth installs a provider-specific login route and callback route. The callback requires an ID token and the
 * `openid` scope, then passes the verified [OidcToken.Id] to [onAuthenticated].
 *
 * Browser sessions are enabled by default. Customize them with [sessions] or opt out with [disableSessions].
 * Plugin-managed [refresh] and [logout] routes require sessions. When sessions are disabled, [onAuthenticated]
 * is required so verified token material is not discarded.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcOAuthConfig internal constructor(
    internal val providerName: String,
) {
    /**
     * OAuth client ID. Required when OAuth is configured.
     *
     * Also used as the expected ID-token audience for callback and refresh validation.
     */
    public lateinit var clientId: String

    /**
     * OAuth client secret. Required when OAuth is configured.
     */
    public lateinit var clientSecret: String

    /**
     * OAuth scopes requested during authorization.
     *
     * The `openid` scope is always required.
     * OAuth callbacks without an ID token are not supported; use Ktor's generic OAuth support for access-token-only login.
     */
    public var scopes: List<String> = listOf("openid", "profile", "email")

    /**
     * Optional resource indicators added to authorization, token, and refresh requests.
     */
    public var resourceIndicators: List<String> = emptyList()

    /**
     * Client authentication method for token endpoint requests: authorization code exchange and token refresh.
     *
     * When `null` (default), the method is selected from
     * [OpenIdProviderMetadata.tokenEndpointAuthMethodsSupported]:
     * - [ClientAuthenticationMethod.ClientSecretPost] when the metadata lists `client_secret_post` as supported,
     *   and when the metadata does not declare supported methods at all.
     * - [ClientAuthenticationMethod.ClientSecretBasic] when the metadata declares supported methods and
     *   `client_secret_post` is not among them.
     *
     * Set explicitly when your client registration requires a specific method — discovery metadata describes the
     * provider's capabilities, not what this client was registered to use — or when provider metadata is inaccurate.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.tokenEndpointAuthMethod)
     */
    public var tokenEndpointAuthMethod: ClientAuthenticationMethod? = null

    /**
     * When `true`, requests the OpenID Provider UserInfo endpoint after token exchange and uses that
     * response for [OidcToken.Id.userInfo].
     *
     * - The request sends the OAuth access token as a Bearer credential.
     * - Signed JWT UserInfo is verified.
     * - Encrypted UserInfo JWTs are rejected.
     * - The UserInfo `sub` claim must match the ID token subject; a mismatch or request failure fails the callback.
     *
     * Also applied when a refresh response includes a new ID token.
     *
     * Defaults to `false`. Then, and when metadata has no `userinfo_endpoint` or the access token is
     * blank, [OidcToken.Id.userInfo] is taken from ID token claims instead.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.fetchUserInfo)
     */
    public var fetchUserInfo: Boolean = false

    /**
     * Symmetric key used to encrypt the in-flight OAuth state cookie carrying `state`, `nonce`, and the PKCE code
     * verifier between the login redirect and the callback.
     *
     * When not set, an ephemeral key is generated and a warning is logged. Configure a shared key so OAuth state
     * cookies remain valid across application restarts and instances.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.stateEncryptionKey)
     */
    public var stateEncryptionKey: OidcStateEncryptionKey? = null

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
     * Configures the OIDC session for this OAuth flow, including cookie transport and CSRF protection.
     *
     * Sessions are enabled by default when [OidcProviderConfig.oauth] is configured. Use this block to customize
     * the secure defaults (`httpOnly`, `secure` in production, `SameSite=lax`), CSRF protection, storage, or
     * refresh strategy.
     *
     * When enabled, the OAuth callback stores the verified [OidcToken.Id] session and plugin-managed refresh/logout
     * routes are installed. Use `authenticateWith(auth0.session)` after [Oidc.identityProvider] to protect routes
     * with that session.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.sessions)
     */
    public fun sessions(configure: OidcSessionsConfig.() -> Unit = {}) {
        sessionsDisabled = false
        sessionConfig = OidcSessionsConfig().apply(configure)
    }

    /**
     * Disables browser sessions for this OAuth flow.
     *
     * Selects callback-only handling without storing an [OidcToken.Id] session. Plugin-managed [refresh] and
     * [logout] routes require sessions and cannot be used after calling this method.
     */
    public fun disableSessions() {
        sessionsDisabled = true
        sessionConfig = null
    }

    /**
     * Configures the plugin-managed logout route.
     *
     * Requires [sessions]. When omitted, defaults to `POST /oidc/{providerName}/logout` with an empty [onLogout] hook
     * and without sending `post_logout_redirect_uri` to the OpenID Provider.
     *
     * The [onLogout] hook runs after the local session is cleared and before the logout redirect.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.logout)
     *
     * @param path log out route path. Query parameters are not supported.
     * @param onLogout hook invoked after the session is cleared.
     */
    public fun logout(
        path: String = "/oidc/$providerName/logout",
        onLogout: suspend RoutingContext.() -> Unit = {},
    ) {
        logoutPath = path
        postLogoutRedirectUri = null
        this.onLogout = onLogout
    }

    /**
     * Configures the plugin-managed logout route and includes `post_logout_redirect_uri` in RP-initiated logout.
     *
     * Requires [sessions]. The [postLogoutRedirectUri] builder must not include query parameters and should be
     * registered with the OpenID Provider when end-session is used.
     *
     * The [onLogout] hook runs after the local session is cleared and before the logout redirect.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.logout)
     *
     * @param path log out route path. Query parameters are not supported.
     * @param postLogoutRedirectUri local redirect URI sent as `post_logout_redirect_uri` and used as fallback when
     * end-session URL cannot be built.
     * @param onLogout hook invoked after the session is cleared.
     */
    public fun logout(
        path: String = "/oidc/$providerName/logout",
        postLogoutRedirectUri: URLBuilder.() -> Unit,
        onLogout: suspend RoutingContext.() -> Unit = {},
    ) {
        logoutPath = path
        this.postLogoutRedirectUri = postLogoutRedirectUri
        this.onLogout = onLogout
    }

    /**
     * Configures the plugin-managed session refresh route.
     *
     * When omitted, defaults to `POST /oidc/{providerName}/refresh` with an empty [onRefresh] hook.
     *
     * The [onRefresh] hook runs after the session is updated with refreshed token material and before the `200 OK`
     * response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcOAuthConfig.refresh)
     *
     * @param path refresh route path.
     * @param onRefresh hook invoked after the session is refreshed.
     */
    public fun refresh(
        path: String = "/oidc/$providerName/refresh",
        onRefresh: suspend RoutingContext.() -> Unit = {},
    ) {
        refreshPath = path
        this.onRefresh = onRefresh
    }

    /**
     * Sets the handler called after a successful OAuth/OIDC login.
     *
     * The callback receives the verified [OidcToken.Id]. With sessions enabled, it runs after the session is stored.
     * Without sessions, it runs after verification and is required, so token material is not discarded.
     *
     * @param block handler invoked with the verified ID-token bundle.
     */
    public fun onAuthenticated(block: OidcOAuthAuthenticatedHandler) {
        onAuthenticated = block
    }

    /**
     * Sets the handler called when OIDC verification fails after token exchange.
     *
     * @param block failure handler.
     */
    public fun onAuthenticationFailed(block: UnauthorizedHandler) {
        onAuthenticationFailed = block
    }

    internal var sessionsDisabled: Boolean = false
    internal var sessionConfig: OidcSessionsConfig? = null

    internal var logoutPath: String? = null
    internal var refreshPath: String? = null

    internal var postLogoutRedirectUri: (URLBuilder.() -> Unit)? = null

    /**
     * Called after a successful OAuth login with the verified [OidcToken.Id].
     */
    internal var onAuthenticated: OidcOAuthAuthenticatedHandler = DEFAULT_ON_AUTHENTICATED

    /**
     * Called when OAuth or OpenID Connect verification fails during the callback.
     */
    internal var onAuthenticationFailed: UnauthorizedHandler = { call.respond(HttpStatusCode.Unauthorized) }

    internal var onLogout: suspend RoutingContext.() -> Unit = {}
    internal var onRefresh: suspend RoutingContext.() -> Unit = {}

    internal fun validate() {
        require(::clientId.isInitialized && clientId.isNotBlank()) {
            "clientId must be configured"
        }
        require(::clientSecret.isInitialized && clientSecret.isNotBlank()) {
            "clientSecret must be configured"
        }
        require("openid" in scopes) {
            "OAuth scopes for OpenID Connect must include openid"
        }
        require(sessionConfig != null || (logoutPath == null && refreshPath == null)) {
            "logout { } and refresh { } require sessions. Call sessions { } or omit disableSessions()"
        }
        require(!sessionsDisabled || onAuthenticated != DEFAULT_ON_AUTHENTICATED) {
            "onAuthenticated { } must be configured when sessions are disabled"
        }
    }

    internal companion object {
        val DEFAULT_ON_AUTHENTICATED: OidcOAuthAuthenticatedHandler = { call.respond(HttpStatusCode.OK) }
    }
}

/**
 * Configuration for OIDC session transport and CSRF protection.
 *
 * Controls how the OpenID Connect session is stored and transported between client and server, and whether CSRF
 * protection is applied to routes authenticated with the enclosing provider.
 *
 * By default, sessions use cookie transport with secure defaults and CSRF protection is enabled with
 * [CSRFConfig.originMatchesHost].
 *
 * Configure via [OidcOAuthConfig.sessions].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcSessionsConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class OidcSessionsConfig internal constructor() {
    /**
     * Cookie / session name.
     *
     * When `null`, defaults to the provider name in uppercase followed by `_SESSION`.
     */
    public var name: String? = null

    /**
     * Server-side session storage. Defaults to in-memory storage.
     *
     * In-memory storage is intended for local development and single-instance deployments. Use shared storage for
     * clustered production deployments.
     */
    public var storage: SessionStorage = SessionStorageMemory()

    /**
     * Strategy used to refresh session token.
     *
     * Disabled by default; expired ID-token sessions are rejected on user routes.
     * Expiry and refresh timing use [OidcToken.Id.claims] [io.ktor.server.auth.oidc.TokenClaims.expiresAt];
     * when the ID token has no `exp` claim, sessions are never treated as expired and auto-refresh never triggers.
     */
    public var tokenRefreshStrategy: OidcTokenRefreshStrategy = OidcTokenRefreshStrategy.Disabled

    /**
     * Configures cookie attributes for the session cookie.
     *
     * The plugin applies secure defaults before this block runs: `httpOnly = true`, `secure = true` (in production),
     * `SameSite = lax`. Values set in this block override those defaults.
     */
    public fun cookie(configure: CookieIdSessionBuilder<OidcToken.Id>.() -> Unit) {
        cookieConfigure = configure
    }

    /**
     * Configures CSRF protection for routes authenticated with this provider's typed session capability.
     *
     * By default, CSRF protection is enabled with [CSRFConfig.originMatchesHost].
     * CSRF checks are applied to plugin-managed POST routes (refresh, logout) and user-defined non-safe HTTP methods
     * under `authenticateWith` for this provider's [OidcProvider.session] scheme.
     */
    public fun csrfProtection(configure: CSRFConfig.() -> Unit) {
        csrfConfigurer = configure
    }

    /**
     * Disables CSRF protection for this provider's routes.
     */
    public fun disableCsrfProtection() {
        csrfConfigurer = null
    }

    internal var cookieConfigure: (CookieIdSessionBuilder<OidcToken.Id>.() -> Unit)? = null

    internal var csrfConfigurer: (CSRFConfig.() -> Unit)? = { originMatchesHost() }
}

internal fun oidcRoutePath(build: URLBuilder.() -> Unit): String {
    val url = URLBuilder().apply(build).build()
    require(url.encodedQuery.isEmpty()) {
        "$url must not include query parameters"
    }
    return url.encodedPath
}

internal fun ApplicationRequest.oidcRedirectUri(build: URLBuilder.() -> Unit): String =
    URLBuilder()
        .apply {
            protocol = URLProtocol.createOrDefault(origin.scheme)
            host = origin.serverHost
            port = origin.serverPort
        }
        .apply(build)
        .buildString()
