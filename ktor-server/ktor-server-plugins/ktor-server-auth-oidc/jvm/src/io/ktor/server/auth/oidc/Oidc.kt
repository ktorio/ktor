/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ProviderNameRegex = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

/**
 * First-class OpenID Connect plugin for Ktor server authentication.
 *
 * Installs per-issuer support for:
 * - Resource-server Bearer authentication (`bearer { }`). JWT Bearer ([OidcProvider.jwtBearer]) and
 *   RFC 7662 introspection Bearer ([OidcProvider.introspectionBearer], via nested `introspection { }`) are
 *   independent schemes. Map them to application principals with [io.ktor.server.auth.mapPrincipal].
 * - **OAuth 2.0 / OIDC login** (`oauth { }`) — authorization code flow with login and callback routes.
 *   Sessions are enabled by default; customize with `oauth { sessions { } }` or opt out with `disableSessions()`.
 *   Browser session authentication is exposed as [OidcProvider.session].
 *
 * This plugin implements the Authorization Code Flow with PKCE (RFC 6749 §4.1, OIDC Core §3.1), resource-server
 * Bearer / RFC 7662 introspection, and optional OAuth 2.0 Protected Resource Metadata (RFC 9728) via
 * [OidcPluginConfig.protectedResource]. Implicit and Hybrid flows are not supported.
 *
 * Provider metadata is fetched automatically from the issuer's discovery document
 * (`<issuer>/.well-known/openid-configuration`) and periodically refreshed unless a provider configures static
 * [OpenIdProviderMetadata].
 *
 * Initial discovery is part of [identityProvider] registration. That suspend function discovers metadata, installs
 * provider routes, and starts periodic refresh before returning the registered [OidcProvider]. After the final failed
 * discovery attempt, registration fails with a [OpenIdDiscoveryException]. Discovery work runs on [Dispatchers.IO].
 *
 * ## Full configuration example
 * The example below registers identity providers from a suspend application module because registration performs
 * initial discovery.
 *
 * ```kotlin
 * val oidc = install(Oidc) {
 *     discoveryRefreshInterval = 15.minutes
 *     initialDiscoveryAttempts = 3
 *     initialDiscoveryRetryDelay = 1.minutes
 * }
 *
 * // One issuer. Schemes expose OidcToken types; map them on the routes that use them.
 * val auth0 = oidc.identityProvider("auth0") {
 *     issuer = "https://issuer"
 *
 *     jwt {
 *         clockSkew = 60.seconds
 *     }
 *
 *     // jwtBearer for locally verified JWTs. Nested introspection { } also enables introspectionBearer.
 *     bearer {
 *         audience = setOf("my-api")
 *         tokenExtractor = { call.request.cookies["MY_TOKEN"] }
 *
 *         introspection {
 *             endpoint = "https://issuer/oauth/introspect"
 *             clientId = "api-client"
 *             clientSecret = "..."
 *         }
 *     }
 *
 *     // Authorization-code login. Sessions store OidcToken.Id unless disableSessions() is called.
 *     oauth {
 *         clientId = "web-client"
 *         clientSecret = "..."
 *         scopes = listOf("openid", "profile", "email")
 *
 *         onAuthenticated { token ->
 *             call.respondRedirect("/dashboard")
 *         }
 *
 *         sessions {
 *             name = "AUTH0_SESSION"
 *         }
 *     }
 * }
 *
 * // Mapping runs when a derived scheme authenticates a route, not during the OAuth callback.
 * val userSession = auth0.session.mapPrincipal { token -> findUser(token.userInfo.subject) }
 * val apiUser = auth0.jwtBearer.mapPrincipal { token -> findUser(token.claims.subject) }
 *
 * routing {
 *     authenticateWith(apiUser) {
 *         get("/api/me") {
 *             val user = call.principal
 *             call.respond(user)
 *         }
 *     }
 *
 *     authenticateWith(auth0.introspectionBearer) {
 *         get("/api/opaque") {
 *             val token = call.principal
 *             call.respond(token.introspection)
 *         }
 *     }
 *
 *     authenticateWith(userSession) {
 *         get("/me") {
 *             val user = call.principal
 *             call.respond(user)
 *         }
 *     }
 * }
 * ```
 *
 * ## Testing with static metadata and local keys
 *
 * Tests can avoid real discovery and JWKS calls while keeping normal issuer, audience, algorithm, and signature
 * validation:
 * ```kotlin
 * val keys = OpenIdTestKeys.rsa(issuer = TEST_ISSUER, audience = TEST_AUDIENCE)
 *
 * // Static metadata skips discovery; jwt(keys) verifies signatures against the in-memory public key.
 * val provider = oidc.identityProvider("test") {
 *     issuer = TEST_ISSUER
 *     metadata = OpenIdProviderMetadata(
 *         issuer = TEST_ISSUER,
 *         authorizationEndpoint = "$TEST_ISSUER/authorize",
 *         tokenEndpoint = "$TEST_ISSUER/token",
 *         jwksUri = "$TEST_ISSUER/jwks",
 *     )
 *
 *     jwt(keys)
 *
 *     bearer {
 *         audience = setOf(TEST_AUDIENCE)
 *     }
 * }
 *
 * val token = keys.accessToken {
 *     subject = "user-1"
 *     email = "user@example.com"
 * }
 * ```
 *
 * ## Environment-based configuration
 *
 * Provider values can be stored in `application.conf` (or equivalent) and applied explicitly with [OidcEnvConfig]:
 * ```hocon
 * ktor.oidc.google {
 *     issuer = "https://accounts.google.com"
 *     clientId = ${GOOGLE_CLIENT_ID}
 *     clientSecret = ${GOOGLE_CLIENT_SECRET}
 *     scopes = ["openid", "profile", "email"]
 * }
 * ```
 *
 * ```kotlin
 * val env = environment.config
 *     .property("ktor.oidc.google")
 *     .getAs<OidcEnvConfig>()
 *
 * val oidc = install(Oidc) { }
 * // env.scopes must include openid; assigning scopes replaces the OAuth default list.
 * val google = oidc.identityProvider("google") {
 *     issuer = env.issuer
 *     bearer {
 *         audience = setOf("api")
 *     }
 *     oauth {
 *         clientId = env.clientId
 *         clientSecret = env.clientSecret
 *         scopes = env.scopes
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.Oidc)
 */
public class Oidc internal constructor(
    private val application: Application,
    private val config: OidcPluginConfig,
    private val client: HttpClient,
) {
    @Volatile
    private var providers = HashMap<String, OidcProvider>()

    private val pendingProviderNames = mutableSetOf<String>()
    private val pendingProviderIssuers = mutableSetOf<String>()
    private val providerRegistrationMutex = Mutex()

    /**
     * Registers an OpenID Connect identity provider (issuer) and returns its typed authentication schemes.
     *
     * - [OidcProvider.session] exposes [OidcToken.Id]
     * - [OidcProvider.jwtBearer] exposes [OidcToken.Access]
     * - [OidcProvider.introspectionBearer] exposes [OidcToken.Introspected]
     *
     * You can map those schemes to application principals with [io.ktor.server.auth.mapPrincipal]. Domain mapping is
     * evaluated only when a derived scheme authenticates a protected route, never during the OAuth callback.
     *
     * ```kotlin
     * val google = oidc.identityProvider("google") {
     *     issuer = "https://accounts.google.com"
     *     bearer { audience = setOf("api") }
     *     oauth {
     *         clientId = "web-client"
     *         clientSecret = "..."
     *     }
     * }
     *
     * val googleScheme = google.jwtBearer.mapPrincipal { token -> findUser(token.claims.subject) }
     * ```
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param configure configures discovery, token validation, Bearer authentication, and OAuth flow.
     * @return configured identity provider.
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend fun identityProvider(
        name: String,
        configure: OidcProviderConfig.() -> Unit,
    ): OidcProvider {
        val config = reserveProviderSlot(name, configure)
        try {
            val provider = discoverProvider(config)
            commitProvider(provider)
            return provider
        } catch (e: Exception) {
            releaseProvider(name, config.issuer)
            throw e
        }
    }

    private suspend fun reserveProviderSlot(
        name: String,
        configure: OidcProviderConfig.() -> Unit
    ): OidcProviderConfig = providerRegistrationMutex.withLock {
        require(name.matches(ProviderNameRegex)) {
            "OpenID Connect provider name $name is invalid. Use lowercase letters, digits, and hyphen-separated segments"
        }
        require(name !in providers && pendingProviderNames.add(name)) {
            "OpenID Connect provider $name is already configured"
        }
        val providerConfig = OidcProviderConfig(name)
        try {
            providerConfig.configure()
            providerConfig.validate()
            val issuer = providerConfig.issuer
            require(providers.values.none { it.issuer == issuer } && pendingProviderIssuers.add(issuer)) {
                "Duplicate OIDC issuer found for provider $name: $issuer"
            }
            providerConfig
        } catch (e: Throwable) {
            pendingProviderNames.remove(name)
            throw e
        }
    }

    private suspend fun discoverProvider(config: OidcProviderConfig): OidcProvider {
        val provider = OidcProvider(config.name, client, config, application.developmentMode)
        val metadata = config.metadata ?: withContext(Dispatchers.IO) {
            discoverInitialMetadata(provider)
        }
        provider.updateMetadata(metadata)
        return provider
    }

    internal fun configureProtectedResourceRoute() {
        config.protectedResourceConfig?.let { protectedResourceConfig ->
            application.configureProtectedResourceRoute(protectedResourceConfig) {
                providers.values.map { it.config }
            }
        }
    }

    private fun resourceMetadataUrl(): String? =
        config.protectedResourceConfig?.let { protectedResourceConfig ->
            require(protectedResourceConfig.resource.isNotBlank()) {
                "protectedResource(resource) must be set to the resource server's identifier URL"
            }
            buildResourceMetadataUrl(protectedResourceConfig.resource)
        }

    private suspend fun commitProvider(provider: OidcProvider) = providerRegistrationMutex.withLock {
        checkProductionEnvironment(provider)
        provider.resourceMetadataUrl = resourceMetadataUrl()
        if (provider.config.oauthConfig != null) {
            application.configureOAuthRoute(provider)
        }
        startRefreshingMetadata(provider)
        providers[provider.name] = provider
        pendingProviderNames.remove(provider.name)
        pendingProviderIssuers.remove(provider.issuer)
    }

    private fun checkProductionEnvironment(provider: OidcProvider) {
        val devMode = application.developmentMode
        val oauthConfig = provider.config.oauthConfig ?: return

        oauthConfig.sessionConfig?.let { sessionConfig ->
            if (!devMode && sessionConfig.storage is SessionStorageMemory) {
                provider.logger.warn(
                    "OpenID Connect is using in-memory session storage (SessionStorageMemory). " +
                        "Sessions will not be shared among application instances and will be lost when the " +
                        "application terminates. Configure shared SessionStorage for production deployments."
                )
            }
        }

        if (oauthConfig.stateEncryptionKey != null) {
            return
        }
        provider.logger.warn(
            "OpenID Connect OAuth stateEncryptionKey is not configured for provider ${provider.name}. " +
                "An ephemeral key was generated for this process. In-flight OAuth logins will fail after restart " +
                "and across application instances. Configure a shared stateEncryptionKey for production deployments."
        )
        oauthConfig.stateEncryptionKey = OidcStateEncryptionKey.random()
    }

    private suspend fun releaseProvider(name: String, issuer: String?) {
        providerRegistrationMutex.withLock {
            pendingProviderNames.remove(name)
            pendingProviderIssuers.remove(issuer)
        }
    }

    private suspend fun discoverInitialMetadata(provider: OidcProvider): OpenIdProviderMetadata {
        val maxAttempts = config.initialDiscoveryAttempts
        repeat(maxAttempts) { attempt ->
            try {
                return client.fetchOpenIdMetadata(provider.issuer)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: IllegalArgumentException) {
                throw cause
            } catch (cause: Throwable) {
                val nextAttempt = attempt + 1

                if (nextAttempt >= maxAttempts) {
                    val message = "Failed to discover OpenID configuration after $maxAttempts attempt(s)"
                    throw OpenIdDiscoveryException(message, cause)
                }
                provider.logger.warn(
                    "OpenID configuration discovery failed. Retrying attempt $nextAttempt/$maxAttempts: ${cause.message}"
                )
                delay(config.initialDiscoveryRetryDelay)
            }
        }
        error("Should not reach here")
    }

    private fun startRefreshingMetadata(provider: OidcProvider) {
        if (provider.config.metadata != null || !config.discoveryRefreshInterval.isPositive()) {
            return
        }
        application.launch(Dispatchers.IO) {
            var hasPreviousFailure = false
            var consecutiveFailures = 0
            while (isActive) {
                val duration = if (hasPreviousFailure) {
                    hasPreviousFailure = false
                    config.discoveryRefreshFailureDelay
                } else {
                    config.discoveryRefreshInterval
                }
                delay(duration)
                try {
                    val newMetadata = client.fetchOpenIdMetadata(provider.issuer)
                    provider.updateMetadata(newMetadata)
                    consecutiveFailures = 0
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    consecutiveFailures++
                    val event = OidcMetadataRefreshFailure(provider, consecutiveFailures, cause)
                    application.monitor.raiseCatching(
                        definition = OidcMetadataRefreshFailed,
                        value = event,
                        logger = provider.logger
                    )
                    hasPreviousFailure = true
                }
            }
        }
    }

    /**
     * Installs [Oidc] in this application and returns the identity-provider registry.
     *
     * Use the returned [Oidc] to call [identityProvider] and keep typed route capabilities close to the
     * issuer configuration. Registration is suspendable because it performs initial discovery, so call it from a
     * suspend application module:
     *
     * ```kotlin
     * val oidc = install(Oidc) {
     *     httpClient = openIdClient
     * }
     *
     * val google = oidc.identityProvider("google") {
     *     issuer = "https://accounts.google.com"
     *     bearer {
     *         audience = setOf("api")
     *     }
     * }
     * val apiUser = google.jwtBearer.mapPrincipal { token -> findUser(token.claims.subject) }
     * ```
     *
     * @return Installed OpenID Connect identity-provider registry.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.Oidc)
     */
    public companion object : BaseApplicationPlugin<Application, OidcPluginConfig, Oidc> {
        override val key: AttributeKey<Oidc> = AttributeKey("Oidc")

        override fun install(
            pipeline: Application,
            configure: OidcPluginConfig.() -> Unit
        ): Oidc {
            val config = OidcPluginConfig().apply {
                configure()
                validate()
            }

            val managedClient = config.httpClient ?: defaultOpenIdHttpClient()
            if (config.httpClient == null) {
                pipeline.monitor.subscribe(ApplicationStopped) { managedClient.close() }
            }

            val plugin = Oidc(
                application = pipeline,
                config = config,
                client = managedClient,
            )
            plugin.configureProtectedResourceRoute()

            pipeline.monitor.subscribe(ApplicationModulesLoaded) {
                if (plugin.providers.isEmpty()) {
                    pipeline.log.warn("No OpenID Connect issuers configured.")
                }
            }

            return plugin
        }
    }
}

/**
 * Serializable OpenID Connect provider values commonly stored in application configuration.
 *
 * The OIDC plugin does not load or merge these values automatically. Read them explicitly and apply them in
 * [Oidc.identityProvider]:
 *
 * ```hocon
 * ktor.oidc.google {
 *     issuer = "https://accounts.google.com"
 *     clientId = ${GOOGLE_CLIENT_ID}
 *     clientSecret = ${GOOGLE_CLIENT_SECRET}
 *     scopes = ["openid", "profile", "email"]
 * }
 * ```
 *
 * ```kotlin
 * val env = environment.config
 *     .property("ktor.oidc.google")
 *     .getAs<OidcEnvConfig>()
 *
 * // OAuth scopes from config must include openid; the plugin does not add it automatically.
 * oidc.identityProvider("google") {
 *     issuer = env.issuer
 *     oauth {
 *         clientId = env.clientId
 *         clientSecret = env.clientSecret
 *         scopes = env.scopes
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcEnvConfig)
 */
@Serializable
public data class OidcEnvConfig(
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
)

/**
 * Details of a failed periodic OpenID Connect discovery metadata refresh.
 * Routes and token validation continue with the last successful discovery document.
 *
 * @property provider OpenID Connect provider instance.
 * @property consecutiveFailures number of consecutive periodic refresh failures, reset after a successful refresh.
 * @property cause failure raised while fetching or validating discovery metadata.
 */
public class OidcMetadataRefreshFailure(
    public val provider: OidcProvider,
    public val consecutiveFailures: Int,
    public val cause: Throwable
)

/**
 * Monitor event raised when a periodic OpenID Connect discovery metadata refresh fails.
 *
 * Subscribe to this event with [Application.monitor]. Initial discovery failures are reported through provider
 * registration exceptions and do not raise this event.
 */
public val OidcMetadataRefreshFailed: EventDefinition<OidcMetadataRefreshFailure> = EventDefinition()

private fun defaultOpenIdHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        val format = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        json(format)
    }
}
