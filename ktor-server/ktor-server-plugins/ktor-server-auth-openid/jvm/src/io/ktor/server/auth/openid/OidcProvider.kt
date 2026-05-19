/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.client.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Configured OpenID Connect provider.
 *
 * @property name provider name.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.openid.OidcProvider)
 */
public class OidcProvider internal constructor(
    public val name: String,
    internal val client: HttpClient,
    internal val config: OidcProviderConfig,
) {
    /**
     * Issuer URL configured for this provider.
     */
    public val issuer: String = config.issuer

    internal val logger: Logger = LoggerFactory.getLogger("io.ktor.server.auth.openid.OidcProvider[$name]")

    @Volatile
    private var providerState: OidcProviderState? = null

    internal fun updateMetadata(newMetadata: OpenIdProviderMetadata) {
        providerState = OidcProviderState(newMetadata)
    }

    /**
     * Returns the currently active OpenID Connect discovery metadata for this provider.
     * The returned value can change after a successful periodic discovery refresh.
     *
     * @throws IllegalStateException when metadata has not been initialized yet.
     */
    public fun currentMetadata(): OpenIdProviderMetadata =
        checkNotNull(providerState) {
            "OpenID Connect metadata is not initialized for provider $name"
        }.metadata
}

private data class OidcProviderState(
    val metadata: OpenIdProviderMetadata,
)
