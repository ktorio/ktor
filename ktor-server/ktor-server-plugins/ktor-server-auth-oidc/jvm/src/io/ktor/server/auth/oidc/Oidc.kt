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
import io.ktor.server.config.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

private val ProviderNameRegex = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

/**
 * First-class OpenID Connect plugin for Ktor server authentication.
 *
 * Installs per-provider support for:
 * - Bearer token authentication (`bearer { }`) that validates JWT access tokens issued by the provider.
 *   Use [OidcProvider.bearer] with `authenticateWith`.
 * - **OAuth 2.0 / OIDC login flow** (`oauth { }`) — handles the authorization code flow,
 *   including login and redirect routes. Session storage is opt-in via `sessions { }`, which also
 *   enables plugin-managed refresh and logout routes.
 *   Registered internally as `"$name-oauth"` and used only for the auto-registered routes;
 *   browser session authentication is exposed as [OidcProvider.sessions].
 *
 * This plugin implements the Authorization Code Flow with PKCE (RFC 6749 §4.1, OIDC Core §3.1), resource-server
 * Bearer / RFC 7662 introspection, and optional OAuth 2.0 Protected Resource Metadata (RFC 9728) via
 * [OidcPluginConfig.protectedResource]. Implicit and Hybrid flows are not supported.
 *
 * Provider metadata is fetched automatically from the issuer's discovery document
 * (`<issuer>/.well-known/openid-configuration`) and periodically refreshed unless a provider configures static
 * [OpenIdProviderMetadata].
 *
 * Initial discovery is part of provider registration. The suspend [provider] functions discover metadata, install
 * provider routes, and start periodic refresh before returning the registered provider. Environment-based
 * configuration is used only as default when a provider with the same name is registered in code; environment
 * entries that are not directly registered are ignored. After the final failed discovery attempt, registration
 * fails with a [OpenIdDiscoveryException]. Discovery work runs on [Dispatchers.IO].
 *
 * ## Full configuration example
 * The example below registers providers from a suspend application module because provider registration performs
 * initial discovery.
 *
 * ```kotlin
 * val oidc = openIdConnect {
 *     // Optional: use a shared HTTP client instead of the plugin's internal one.
 *     // httpClient = myHttpClient
 *
 *     // How often to re-fetch the discovery document. Set to ZERO to disable.
 *     discoveryRefreshInterval = 15.minutes
 *
 *     // Provider registration waits for initial discovery and fails after the final unsuccessful attempt.
 *     // Defaults to one attempt.
 *     initialDiscoveryAttempts = 3
 *     initialDiscoveryRetryDelay = 1.minutes
 *
 * }
 *
 * val google = oidc.provider("google") {
 *     issuer = "https://accounts.google.com"
 *
 *     // JWT settings shared by ID-token and JWT access-token verification.
 *     jwt {
 *         clockSkew = 60.seconds
 *     }
 *
 *     // Access-token policy. Configure resource audiences explicitly.
 *     accessToken {
 *         audiences = setOf("my-api")
 *     }
 *
 *     // Bearer token authentication — protects API routes via Bearer tokens.
 *     bearer {
 *         // Optional: customise where the token is extracted from.
 *         tokenExtractor = { call -> call.request.cookies["MY_TOKEN"] }
 *     }
 *
 *     // OAuth/OIDC login flow — installs login and callback routes.
 *     oauth {
 *         clientId = System.getenv("GOOGLE_CLIENT_ID")
 *         clientSecret = System.getenv("GOOGLE_CLIENT_SECRET")
 *         scopes = listOf("openid", "profile", "email")
 *
 *         onSuccess {
 *             call.respondRedirect("/dashboard")
 *         }
 *     }
 *
 *     // Browser sessions are opt-in. When enabled, callbacks store the verified
 *     // ID-token principal in the session and plugin-managed refresh/logout routes
 *     // are installed.
 *     sessions {
 *         name = "GOOGLE_SESSION"
 *     }
 * }
 *
 * // Protect routes using typed provider capabilities.
 * routing {
 *     authenticateWith(google.bearer) {
 *         get("/profile") {
 *             val user = principal as OidcToken.Access
 *             call.respond("Logged in as ${user.userInfo.name}")
 *         }
 *     }
 *
 *     authenticateWith(google.bearer) {
 *         get("/api/me") {
 *             val user = principal as OidcToken.Access
 *             call.respond(user.userInfo)
 *         }
 *     }
 *
 *     authenticateWith(google.sessions) {
 *         get("/me") {
 *             val user = principal as OidcToken.Id
 *             call.respond(user.userInfo)
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
 * val provider = oidc.provider<UserPrincipal>("test") {
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
 *     accessToken {
 *         audiences = setOf(TEST_AUDIENCE)
 *     }
 *
 *     bearer()
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
 * Provider defaults can also be loaded from `application.conf` (or equivalent):
 * ```hocon
 * ktor.oidc.google {
 *     issuer = "https://accounts.google.com"
 *     clientId = ${GOOGLE_CLIENT_ID}
 *     clientSecret = ${GOOGLE_CLIENT_SECRET}
 *     scopes = ["openid", "profile", "email"]
 * }
 * ```
 *
 * Environment-based entries are applied by declaring the same provider name on the installed [Oidc]
 * instance:
 * ```kotlin
 * val oidc = openIdConnect { }
 * val google = oidc.provider("google") {
 *     accessToken {
 *         audiences = setOf("api")
 *     }
 *     bearer()
 *     oauth()
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
    private val environmentProviders = HashMap<String, EnvConfig>()

    @Volatile
    private var providers = HashMap<String, OidcProvider<*>>()

    private val pendingProviderNames = mutableSetOf<String>()
    private val pendingProviderIssuers = mutableSetOf<String>()
    private val providerRegistrationMutex = Mutex()

    @PublishedApi
    internal suspend fun <P : Any> provider(
        name: String,
        principalType: KClass<P>,
        transformPrincipal: PrincipalTransformer<P>,
        configure: OidcProviderConfig<P>.() -> Unit,
    ): OidcProvider<P> {
        val config = reserveProviderName(name, principalType, transformPrincipal, configure)
        try {
            val provider = discoverProvider(config)
            commitProvider(provider)
            return provider
        } catch (e: Exception) {
            releaseProvider(name, config.issuer)
            throw e
        }
    }

    /**
     * Configures an OpenID Connect provider for custom route principal type [P].
     *
     * The [transformPrincipal] callback maps a verified [OidcToken] to [P]. Return `null` to reject
     * the verified token for this provider.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param transformPrincipal maps verified OpenID Connect principals to the typed route principal.
     * @param configure configures discovery, token validation, Bearer authentication, and OAuth flow.
     * @return configured provider whose route-facing capabilities use principal type [P].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend inline fun <reified P : Any> provider(
        name: String,
        noinline transformPrincipal: PrincipalTransformer<P>,
        noinline configure: OidcProviderConfig<P>.() -> Unit,
    ): OidcProvider<P> = provider(name, principalType = P::class, transformPrincipal, configure)

    /**
     * Configures an OpenID Connect provider that exposes [OidcToken] directly to routes.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param configure configures discovery, token validation, Bearer authentication, and OAuth flow.
     * @return configured provider whose route-facing capabilities use [OidcToken].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend fun provider(
        name: String,
        configure: OidcProviderConfig<OidcToken>.() -> Unit,
    ): OidcProvider<OidcToken> = provider(name, transformPrincipal = { it }, configure)

    private suspend fun <P : Any> reserveProviderName(
        name: String,
        principalType: KClass<P>,
        transformPrincipal: PrincipalTransformer<P>,
        configure: OidcProviderConfig<P>.() -> Unit
    ): OidcProviderConfig<P> = providerRegistrationMutex.withLock {
        require(name.matches(ProviderNameRegex)) {
            "OpenID Connect provider name $name is invalid. Use lowercase letters, digits, and hyphen-separated segments"
        }
        require(name !in providers && pendingProviderNames.add(name)) {
            "OpenID Connect provider $name is already configured"
        }
        val providerConfig = OidcProviderConfig(name, principalType).apply {
            environmentProviders[name]?.let { env -> applyEnvDefaults(env) }
        }
        try {
            providerConfig.configure()
            providerConfig.validate()
            providerConfig.principalTransformer = transformPrincipal
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

    private suspend fun <P : Any> discoverProvider(config: OidcProviderConfig<P>): OidcProvider<P> {
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

    private suspend fun commitProvider(provider: OidcProvider<*>) = providerRegistrationMutex.withLock {
        checkProductionEnvironment(provider)
        provider.resourceMetadataUrl = resourceMetadataUrl()
        if (provider.config.oauthConfig != null) {
            application.configureOAuthRoute(provider)
        }
        startRefreshingMetadata(provider)
        providers[provider.name] = provider
        environmentProviders.remove(provider.name)
        pendingProviderNames.remove(provider.name)
        pendingProviderIssuers.remove(provider.issuer)
    }

    private fun checkProductionEnvironment(provider: OidcProvider<*>) {
        val devMode = application.developmentMode

        // check production session storage is not in-memory
        provider.config.sessionConfig?.let { sessionConfig ->
            if (!devMode && sessionConfig.storage is SessionStorageMemory) {
                provider.logger.warn(
                    "OpenID Connect sessions use SessionStorageMemory. Configure shared SessionStorage for clustered production deployments."
                )
            }
        }

        // ensure production stateEncryptionKey is configured
        val oauthConfig = provider.config.oauthConfig
        if (oauthConfig == null || oauthConfig.stateEncryptionKey != null) {
            return
        }
        if (devMode) {
            provider.logger.warn("OpenID Connect OAuth stateEncryptionKey is not configured.")
            oauthConfig.stateEncryptionKey = OidcStateEncryptionKey.random()
        } else {
            error(
                "OpenID Connect OAuth provider ${provider.name} cannot start in production without stateEncryptionKey"
            )
        }
    }

    private suspend fun releaseProvider(name: String, issuer: String?) {
        providerRegistrationMutex.withLock {
            pendingProviderNames.remove(name)
            pendingProviderIssuers.remove(issuer)
        }
    }

    private suspend fun discoverInitialMetadata(provider: OidcProvider<*>): OpenIdProviderMetadata {
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

    private fun startRefreshingMetadata(provider: OidcProvider<*>) {
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

    private fun loadConfigFromEnvironment() {
        val config = application.environment.config
        if (config.propertyOrNull("ktor.oidc") == null) {
            return
        }
        val root = config.config("ktor.oidc")
        root.keys().map { it.substringBefore(".") }.distinct().forEach { providerName ->
            val env = root.property(providerName).getAs<EnvConfig>()
            require((env.clientId == null) == (env.clientSecret == null)) {
                "OpenID Connect provider $providerName must configure both clientId and clientSecret, or neither"
            }
            environmentProviders[providerName] = env
        }
    }

    public companion object : BaseApplicationPlugin<Application, OidcPluginConfig, Oidc> {
        override val key: AttributeKey<Oidc> = AttributeKey("Oidc")

        @OptIn(ExperimentalKtorApi::class)
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
            plugin.loadConfigFromEnvironment()
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
 * Represents a single configured OpenID Connect provider loaded from the environment.
 */
@Serializable
internal data class EnvConfig(
    val issuer: String,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val scopes: List<String> = listOf("openid", "profile", "email"),
)

private fun <P : Any> OidcProviderConfig<P>.applyEnvDefaults(env: EnvConfig) = apply {
    issuer = env.issuer
    env.clientId?.let { id ->
        oauth {
            clientId = id
            clientSecret = env.clientSecret!!
            scopes = env.scopes
        }
    }
}

/**
 * Details of a failed periodic OpenID Connect discovery metadata refresh.
 * Routes and token validation continue with the last successful discovery document.
 *
 * @property provider OpenID Connect provider instance.
 * @property consecutiveFailures number of consecutive periodic refresh failures, reset after a successful refresh.
 * @property cause failure raised while fetching or validating discovery metadata.
 */
public class OidcMetadataRefreshFailure(
    public val provider: OidcProvider<*>,
    public val consecutiveFailures: Int,
    public val cause: Throwable
) {
    public val causedByValidation: Boolean = cause is IllegalArgumentException
}

/**
 * Monitor event raised when a periodic OpenID Connect discovery metadata refresh fails.
 *
 * Subscribe to this event with [Application.monitor]. Initial discovery failures are reported through provider
 * registration exceptions and do not raise this event.
 */
public val OidcMetadataRefreshFailed: EventDefinition<OidcMetadataRefreshFailure> = EventDefinition()

/**
 * Installs [Oidc] in this application and returns the provider registry.
 *
 * Use the returned [Oidc] to declare providers and keep typed route capabilities close to the
 * provider configuration. Provider registration is suspendable because it performs initial discovery, so call it from a
 * suspend application module:
 *
 * ```kotlin
 * val oidc = openIdConnect {
 *     httpClient = openIdClient
 * }
 *
 * val google = oidc.provider("google") {
 *     issuer = "https://accounts.google.com"
 *     accessToken {
 *         audiences = setOf("api")
 *     }
 *     bearer()
 * }
 * ```
 *
 * @param configure Plugin configuration block.
 * @return Installed OpenID Connect provider registry.
 * @throws IllegalArgumentException when environment-based provider configuration is invalid.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.openIdConnect)
 */
public fun Application.openIdConnect(
    configure: OidcPluginConfig.() -> Unit = {},
): Oidc =
    install(Oidc, configure)

/**
 * Returns the installed [Oidc] plugin instance.
 *
 * Convenience wrapper for `plugin(Oidc)`.
 *
 * @return Installed OpenID Connect plugin instance.
 * @throws MissingApplicationPluginException when [Oidc] is not installed.
 */
public fun Application.openIdConnect(): Oidc = plugin(Oidc)

private fun defaultOpenIdHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        val format = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        json(format)
    }
}
