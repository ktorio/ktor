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

private val ProviderNameRegex = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

/**
 * First-class OpenID Connect plugin for Ktor server authentication.
 *
 * Providers are registered from a suspend application module because registration performs initial discovery.
 * Provider metadata is fetched from the issuer's discovery document
 * (`<issuer>/.well-known/openid-configuration`) and periodically refreshed.
 *
 * Environment-based configuration is used only as a default when a provider with the same name is registered in code;
 * environment entries that are not directly registered are ignored. After the final failed discovery attempt,
 * registration fails with a [OpenIdDiscoveryException]. Discovery work runs on [Dispatchers.IO].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.Oidc)
 */
public class Oidc internal constructor(
    private val application: Application,
    private val config: OidcPluginConfig,
    private val client: HttpClient,
) {
    @Volatile
    private var providers: Map<String, OidcProvider> = linkedMapOf()

    private val pendingProviderNames = mutableSetOf<String>()
    private val pendingProviderIssuers = mutableSetOf<String>()
    private val providerRegistrationMutex = Mutex()

    /**
     * Configures an OpenID Connect provider.
     *
     * @param name provider name. Must contain lowercase letters, digits, and hyphen-separated segments only.
     * @param configure configures provider discovery settings.
     * @return configured provider.
     * @throws IllegalArgumentException when [name] or issuer is already configured, or the provider
     * configuration is invalid.
     * @throws OpenIdDiscoveryException when initial provider discovery fails after all configured attempts.
     */
    public suspend fun provider(
        name: String,
        configure: OidcProviderConfig.() -> Unit,
    ): OidcProvider {
        val config = reserveProviderName(name, configure)
        try {
            val provider = discoverProvider(config)
            commitProvider(provider)
            return provider
        } catch (e: Exception) {
            releaseProvider(name, config.issuer)
            throw e
        }
    }

    private suspend fun reserveProviderName(
        name: String,
        configure: OidcProviderConfig.() -> Unit
    ): OidcProviderConfig = providerRegistrationMutex.withLock {
        require(name.matches(ProviderNameRegex)) {
            "OpenID Connect provider name $name is invalid. Use lowercase letters, digits, and hyphen-separated segments"
        }
        require(name !in providers && pendingProviderNames.add(name)) {
            "OpenID Connect provider $name is already configured"
        }

        val providerConfig = OidcProviderConfig(name).apply {
            config.environmentProviders[name]?.let { env -> issuer = env.issuer }
        }
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
        val provider = OidcProvider(config.name, client, config)
        val metadata = withContext(Dispatchers.IO) {
            discoverInitialMetadata(provider)
        }
        provider.updateMetadata(metadata)
        return provider
    }

    private suspend fun commitProvider(provider: OidcProvider) = providerRegistrationMutex.withLock {
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

    private suspend fun discoverInitialMetadata(provider: OidcProvider): OpenIdProviderMetadata {
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
        throw OpenIdDiscoveryException(
            "Failed to discover OpenID configuration after ${config.initialDiscoveryAttempts} attempt(s)",
            lastFailure,
        )
    }

    private fun Application.startRefreshingMetadata(provider: OidcProvider) {
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
        override val key: AttributeKey<Oidc> = AttributeKey("OIDC")

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
 * Use the returned [Oidc] to declare providers and keep provider configuration close to application setup.
 * Provider registration is suspendable because it performs initial discovery.
 *
 * @param configure plugin configuration block.
 * @return installed OpenID Connect provider registry.
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
 * @return installed OpenID Connect plugin instance.
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
