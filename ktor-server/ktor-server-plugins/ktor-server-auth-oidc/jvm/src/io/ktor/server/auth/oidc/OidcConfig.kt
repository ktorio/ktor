/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration
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
     * fails with [DiscoveryException].
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
 * @property name provider name used by this application.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcProviderConfig)
 */
@KtorDsl
public class OidcProviderConfig internal constructor(
    public val name: String,
) {
    /**
     * Issuer URL. Used for OpenID Connect discovery (`<issuer>/.well-known/openid-configuration`).
     */
    public lateinit var issuer: String

    internal fun validate() {
        require(::issuer.isInitialized && issuer.isNotBlank()) {
            "issuer must be configured"
        }
    }
}
