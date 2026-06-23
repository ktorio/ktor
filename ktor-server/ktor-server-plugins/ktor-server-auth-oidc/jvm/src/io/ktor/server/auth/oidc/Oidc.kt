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
 * Installs per-provider Bearer token authentication (`bearer { }`) that validates JWT access tokens issued by the
 * provider. Use [OidcProvider.bearer] with `authenticateWith`.
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
 *     // JWT settings used for access-token verification.
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
 * }
 *
 * // Protect routes using typed provider capabilities.
 * routing {
 *     authenticateWith<OidcToken>(google.bearer) {
 *         get("/profile") {
 *             val user = principal as OidcToken.Access
 *             call.respond("Logged in as ${user.userInfo?.name}")
 *         }
 *     }
 *
 *     authenticateWith(google.bearer) {
 *         get("/api/me") {
 *             val user = principal as OidcToken.Access
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
     * The [transformPrincipal] callback maps a verified [OidcToken] to [P]. Return `null` to reject
     * the verified token for this provider.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param transformPrincipal maps verified OpenID Connect principals to the typed route principal.
     * @param configure configures discovery, token validation, and Bearer authentication.
     * @return configured provider whose route-facing capabilities use principal type [P].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend inline fun <reified P : Any> provider(
        name: String,
        noinline transformPrincipal: PrincipalTransformer<P>,
        noinline configure: OidcProviderConfig<P>.() -> Unit,
    ): OidcProvider<P> =
        provider(name, principalType = P::class, transformPrincipal, configure)

    /**
     * Configures an OpenID Connect provider that exposes [OidcToken] directly to routes.
     *
     * @param name provider name used in generated routes and authentication scheme names. Must contain lowercase
     * letters, digits, and hyphen-separated segments only.
     * @param configure configures discovery, token validation, and Bearer authentication.
     * @return configured provider whose route-facing capabilities use [OidcToken].
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend fun provider(
        name: String,
        configure: OidcProviderConfig<OidcToken>.() -> Unit,
    ): OidcProvider<OidcToken> =
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
        val provider = OidcProvider(config.name, client, config, application.developmentMode)
        val metadata = config.metadata ?: withContext(Dispatchers.IO) {
            discoverInitialMetadata(provider)
        }
        provider.updateMetadata(metadata)
        return provider
    }

    private suspend fun commitProvider(provider: OidcProvider<*>) = providerRegistrationMutex.withLock {
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

    private fun Application.startRefreshingMetadata(provider: OidcProvider<*>) {
        if (provider.config.metadata != null || !config.discoveryRefreshInterval.isPositive()) {
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
                    val newMetadata = client.fetchOpenIdMetadata(provider.issuer)
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
