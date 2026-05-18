/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

private val ProviderNameRegex = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

/**
 * First-class OpenID Connect plugin for Ktor server authentication.
 *
 * Installs per-provider support for:
 * - **Bearer token authentication** (`bearer { }`) — validates Bearer tokens issued by the provider.
 *   Use [OidcProvider.bearer] with `authenticateWith`.
 * - **OAuth 2.0 / OIDC login flow** (`oauth { }`) — handles the authorization code flow,
 *   including login and redirect routes. Session storage is opt-in via `sessions { }`, which also
 *   enables plugin-managed refresh and logout routes.
 *   Registered internally as `"$name-oauth"` and used only for the auto-registered routes;
 *   browser session authentication is exposed as [OidcProvider.sessions].
 * - **Protected Resource Metadata** (`protectedResource { }`) — optionally serves an
 *   [RFC 9728](https://www.rfc-editor.org/rfc/rfc9728) metadata endpoint at
 *   `/.well-known/oauth-protected-resource`, advertising which authorization servers this
 *   resource trusts, supported scopes, bearer methods, and more. When enabled, 401 responses
 *   include a `resource_metadata` parameter in the `WWW-Authenticate` header.
 *
 * Provider metadata is fetched automatically from the issuer's discovery document
 * (`<issuer>/.well-known/openid-configuration`) and periodically refreshed.
 *
 * Initial discovery is part of provider registration. The suspend [provider] functions discover metadata, install
 * provider routes, and start periodic refresh before returning the registered provider. Environment-based
 * configuration is used only as default when a provider with the same name is registered in code; environment
 * entries that are not directly registered are ignored. After the final failed discovery attempt, registration
 * fails with a [DiscoveryException]. Discovery work runs on [Dispatchers.IO].
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
 * // ── Provider with both JWT verification and OAuth login ──────────────
 * val google = oidc.provider("google") {
 *     issuer = "https://accounts.google.com"
 *
 *     // JWT settings shared by ID-token and JWT access-token verification.
 *     jwt {
 *         clockSkewSeconds = 60
 *     }
 *
 *     // Access-token policy. Access-token audiences never default to oauth.clientId.
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
 *     // OAuth/OIDC login flow — installs login and redirect routes.
 *     oauth {
 *         clientId = System.getenv("GOOGLE_CLIENT_ID")
 *         clientSecret = System.getenv("GOOGLE_CLIENT_SECRET")
 *         scopes = listOf("openid", "profile", "email")
 *
 *         // Routes (defaults: /oidc/{providerName}/login and /callback).
 *         loginUri { path("auth", "login") }
 *         redirectUri { path("auth", "callback") }
 *
 *         // Called after successful login.
 *         onSuccess { principal ->
 *             call.respondRedirect("/dashboard")
 *         }
 *
 *         onFailure { cause ->
 *             call.respond(HttpStatusCode.Unauthorized)
 *         }
 *     }
 * }
 *
 * // ── Bearer-only provider (no OAuth login flow) ────────────────────────
 * val internal = oidc.provider("internal") {
 *     issuer = "https://internal.example.com"
 *     accessToken {
 *         audiences = setOf("internal-service")
 *     }
 *     bearer()
 * }
 *
 * // Protect routes using typed provider capabilities.
 * // The plugin auto-registers OAuth login/redirect routes internally.
 * routing {
 *     authenticateWith<OidcPrincipal>(google.bearer) {
 *         get("/profile") {
 *             val user = principal as OidcPrincipal.AccessToken
 *             call.respond("Logged in as ${user.userInfo.name}")
 *         }
 *     }
 *
 *     authenticateWith(google.bearer) {
 *         get("/api/me") {
 *             val user = principal as OidcPrincipal.AccessToken
 *             call.respond(user.userInfo)
 *         }
 *     }
 * }
 * ```
 *
 * ## Environment-based configuration
 *
 * Provider defaults can also be loaded from `application.conf` (or equivalent):
 * ```hocon
 * ktor.openid.google {
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
 *     oauth {
 *         onSuccess { principal ->
 *             call.respondText("signed in")
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.Oidc)
 */
public class Oidc internal constructor(
    private val application: Application,
    private val config: OidcPluginConfig,
    private val client: HttpClient,
) {
    @Volatile
    private var providers: Map<String, OidcProvider<*>> = linkedMapOf()

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
     * The [transformPrincipal] callback maps a verified [OidcPrincipal] to [P]. Return `null` to reject
     * the verified token for this provider.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param transformPrincipal maps verified OpenID Connect principals to the typed route principal.
     * @param configure configures discovery, token validation, Bearer authentication, OAuth flow, and sessions.
     * @return configured provider whose route-facing capabilities use principal type [P].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws DiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend inline fun <reified P : Any> provider(
        name: String,
        noinline transformPrincipal: PrincipalTransformer<P>,
        noinline configure: OidcProviderConfig<P>.() -> Unit,
    ): OidcProvider<P> =
        provider(name, principalType = P::class, transformPrincipal, configure)

    /**
     * Configures an OpenID Connect provider that exposes [OidcPrincipal] directly to routes.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param configure configures discovery, token validation, Bearer authentication, OAuth flow, and sessions.
     * @return configured provider whose route-facing capabilities use [OidcPrincipal].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws DiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend fun provider(
        name: String,
        configure: OidcProviderConfig<OidcPrincipal>.() -> Unit,
    ): OidcProvider<OidcPrincipal> =
        provider(name, transformPrincipal = { it }, configure)

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
            config.environmentProviders[name]?.let { env -> applyEnvDefaults(env) }
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
        val provider = OidcProvider(config.name, client, config)
        val metadata = withContext(Dispatchers.IO) {
            discoverInitialMetadata(provider)
        }
        provider.updateMetadata(metadata)
        return provider
    }

    private suspend fun commitProvider(provider: OidcProvider<*>) = providerRegistrationMutex.withLock {
        provider.createSchemes()
        application.configureOAuthRoute(provider)
        application.startRefreshingMetadata(provider)
        providers = providers + (provider.name to provider)
        config.environmentProviders.remove(provider.name)
        pendingProviderNames.remove(provider.name)
        pendingProviderIssuers.remove(provider.issuer)
    }

    private suspend fun releaseProvider(name: String, issuer: String?) {
        providerRegistrationMutex.withLock {
            pendingProviderNames.remove(name)
            pendingProviderIssuers.remove(issuer)
        }
    }

    private suspend fun discoverInitialMetadata(provider: OidcProvider<*>): OpenIdProviderMetadata {
        var lastFailure: Throwable? = null
        repeat(config.initialDiscoveryAttempts) { attempt ->
            try {
                return client.discoverVerified(provider.issuer)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: IllegalArgumentException) {
                throw cause
            } catch (cause: Throwable) {
                lastFailure = cause
                val nextAttempt = attempt + 1
                if (nextAttempt < config.initialDiscoveryAttempts) {
                    provider.logger.warn(
                        "OpenID configuration discovery failed. Retrying attempt ${nextAttempt + 1}/${config.initialDiscoveryAttempts}: ${cause.message}"
                    )
                    delay(config.initialDiscoveryRetryDelay)
                }
            }
        }
        throw DiscoveryException(
            "Failed to discover OpenID configuration after ${config.initialDiscoveryAttempts} attempt(s)",
            lastFailure,
        )
    }

    private fun Application.startRefreshingMetadata(provider: OidcProvider<*>) {
        if (!config.discoveryRefreshInterval.isPositive()) {
            return
        }
        launch(Dispatchers.IO) {
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
                    val newMetadata = client.discoverVerified(provider.issuer)
                    provider.updateMetadata(newMetadata)
                    consecutiveFailures = 0
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    consecutiveFailures++
                    val event = OidcMetadataRefreshFailure(provider, consecutiveFailures, cause)
                    monitor.raiseCatching(
                        definition = OidcMetadataRefreshFailed,
                        value = event,
                        logger = provider.logger
                    )
                    hasPreviousFailure = true
                }
            }
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
                pipeline.loadConfigFromEnvironment()
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

            pipeline.monitor.subscribe(ApplicationModulesLoaded) {
                if (plugin.providers.isEmpty()) {
                    pipeline.log.warn("No OpenID Connect issuers configured.")
                }
            }

            return plugin
        }
    }
}

private fun <P : Any> OidcProviderConfig<P>.applyEnvDefaults(env: OidcPluginConfig.EnvConfig) =
    apply {
        issuer = env.issuer
        if (env.clientId != null) {
            oauth {
                clientId = env.clientId
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
