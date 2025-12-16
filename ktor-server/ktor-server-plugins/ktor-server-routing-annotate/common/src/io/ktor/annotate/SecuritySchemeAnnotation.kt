/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.openapi.*
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.ApiKeyAuthenticationProvider
import io.ktor.server.sessions.SessionProvidersKey
import io.ktor.server.sessions.SessionTransportCookie
import io.ktor.server.sessions.SessionTransportHeader
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set

/**
 * Attribute key for storing OpenAPI security scheme metadata for authentication providers.
 * Maps provider names to their corresponding SecurityScheme definitions.
 */
public val AuthSecuritySchemesAttributeKey: AttributeKey<Map<String, SecurityScheme>> =
    AttributeKey("AuthSecuritySchemes")

internal val AuthSecuritySchemesCacheAttributeKey: AttributeKey<Map<String, SecurityScheme>> =
    AttributeKey("AuthSecuritySchemesCache")

/**
 * Registers a security scheme for an authentication provider.
 * This metadata will be used to generate the OpenAPI specification.
 * It's not recommended to use this method after the application has started.
 *
 * @param providerName The name of the authentication provider. Defaults to "default".
 * @param securityScheme The OpenAPI security scheme definition.
 */
public fun Application.registerSecurityScheme(
    providerName: String?,
    securityScheme: SecurityScheme
) {
    val providerName = providerName ?: AuthenticationRouteSelector.DEFAULT_NAME
    val existingSchemes = attributes.getOrNull(AuthSecuritySchemesAttributeKey) ?: emptyMap()
    attributes.put(AuthSecuritySchemesAttributeKey, existingSchemes + (providerName to securityScheme))
}

/**
 * Retrieves all registered security schemes from the application.
 *
 * @param useCache Whether to use a cached value if available. Enabled by default.
 *
 * @return A map of provider names to their security schemes, or null if no schemes are available.
 */
public fun Application.findSecuritySchemes(useCache: Boolean = true): Map<String, SecurityScheme>? {
    val cachedSchemes = this.attributes.getOrNull(AuthSecuritySchemesCacheAttributeKey)
    if (useCache && cachedSchemes != null) {
        return cachedSchemes.takeIf { it.isNotEmpty() }
    }
    val manualSchemes = attributes.getOrNull(AuthSecuritySchemesAttributeKey)
    val inferredSchemes = inferSecuritySchemesFromAuthentication()
    val mergedSchemes = when {
        manualSchemes != null && inferredSchemes != null -> inferredSchemes + manualSchemes
        manualSchemes != null -> manualSchemes
        inferredSchemes != null -> inferredSchemes
        else -> null
    }
    if (useCache) {
        attributes[AuthSecuritySchemesCacheAttributeKey] = mergedSchemes ?: emptyMap()
    }
    return mergedSchemes
}

/**
 * Retrieves all registered security schemes from the application.
 *
 * @param useCache Whether to use a cached value if available. Enabled by default.
 *
 * @return A map of provider names to their security schemes wrapped in ReferenceOr<SecurityScheme>, or null if no schemes are available.
 */
public fun Application.findSecuritySchemesOrRefs(
    useCache: Boolean = true
): Map<String, ReferenceOr<SecurityScheme>>? {
    return findSecuritySchemes(useCache)?.let {
        buildMap {
            for ((providerName, securityScheme) in it) {
                set(providerName, value(securityScheme))
            }
        }
    }
}

internal val Application.isAuthPluginInstalled: Boolean
    get() = runCatching { pluginOrNull(Authentication) != null }.getOrElse { false }

@OptIn(InternalAPI::class)
private fun Application.inferSecuritySchemesFromAuthentication(): Map<String, SecurityScheme>? = buildMap {
    if (!isAuthPluginInstalled) return null
    val authPlugin = pluginOrNull(Authentication) ?: return null
    val providers = authPlugin.configuration().allProviders()
    for ((providerName, provider) in providers) {
        inferSecurityScheme(provider)?.let {
            set(providerName ?: AuthenticationRouteSelector.DEFAULT_NAME, it)
        }
    }
}.ifEmpty { null }

/**
 * Infers the OpenAPI security scheme from an authentication provider based on its type.
 */
@OptIn(InternalAPI::class)
private fun Application.inferSecurityScheme(provider: AuthenticationProvider): SecurityScheme? {
    return when (provider) {
        is BasicAuthenticationProvider -> HttpSecurityScheme(
            scheme = "basic",
            description = provider.description ?: HttpSecurityScheme.DEFAULT_BASIC_DESCRIPTION
        )

        is BearerAuthenticationProvider -> HttpSecurityScheme(
            scheme = "bearer",
            description = provider.description ?: HttpSecurityScheme.DEFAULT_BEARER_DESCRIPTION,
        )

        is SessionAuthenticationProvider<*> -> {
            val sessionProvider =
                attributes.getOrNull(SessionProvidersKey)?.firstOrNull { it.type == provider.type } ?: return null
            val keyLocation = when (sessionProvider.transport) {
                is SessionTransportCookie -> SecuritySchemeIn.COOKIE
                is SessionTransportHeader -> SecuritySchemeIn.HEADER
                else -> return null
            }
            val keyName = when (val t = sessionProvider.transport) {
                is SessionTransportCookie -> t.name
                is SessionTransportHeader -> t.name
                else -> return null
            }
            ApiKeySecurityScheme(
                name = keyName,
                `in` = keyLocation,
                description = provider.description ?: "Session-based Authentication"
            )
        }

        is ApiKeyAuthenticationProvider -> {
            ApiKeySecurityScheme(
                name = provider.headerName,
                `in` = SecuritySchemeIn.HEADER,
                description = provider.description ?: ApiKeySecurityScheme.DEFAULT_DESCRIPTION
            )
        }

        is OAuthAuthenticationProvider -> {
            val settings = provider.staticSettings() as? OAuthServerSettings.OAuth2ServerSettings ?: return null
            OAuth2SecurityScheme(
                flows = OAuthFlows(
                    authorizationCode = OAuthFlow(
                        authorizationUrl = settings.authorizeUrl,
                        tokenUrl = settings.accessTokenUrl,
                        refreshUrl = settings.refreshUrl,
                        scopes = settings.defaultScopeDescriptions.takeIf { it.isNotEmpty() }
                            ?: settings.defaultScopes.associateWith { OAuthFlow.DEFAULT_SCOPE_DESCRIPTION }
                    )
                ),
                description = provider.description ?: OAuth2SecurityScheme.DEFAULT_DESCRIPTION
            )
        }

        else -> inferPlatformSpecificSecurityScheme(provider)
    }
}

internal expect fun Application.inferPlatformSpecificSecurityScheme(provider: AuthenticationProvider): SecurityScheme?

/**
 * Registers a Basic HTTP authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param description Optional description for the security scheme. Defaults to "HTTP Basic Authentication".
 */
public fun Application.registerBasicAuthSecurityScheme(
    name: String? = null,
    description: String = HttpSecurityScheme.DEFAULT_BASIC_DESCRIPTION
) {
    registerSecurityScheme(name, HttpSecurityScheme(scheme = "basic", description = description))
}

/**
 * Registers a Bearer HTTP authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param description Optional description for the security scheme. Defaults to "HTTP Bearer Authentication".
 * @param bearerFormat Optional hint about the bearer token format (e.g., "JWT").
 */
public fun Application.registerBearerAuthSecurityScheme(
    name: String? = null,
    description: String = HttpSecurityScheme.DEFAULT_BEARER_DESCRIPTION,
    bearerFormat: String? = null
) {
    registerSecurityScheme(
        name,
        HttpSecurityScheme(scheme = "bearer", bearerFormat = bearerFormat, description = description)
    )
}

/**
 * Registers an API Key authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param keyName The name of the header, query, or cookie parameter.
 * @param keyLocation The location of the API key (header, query, or cookie).
 * @param description Optional description for the security scheme. Defaults to "API Key Authentication".
 */
public fun Application.registerApiKeySecurityScheme(
    name: String? = null,
    keyName: String,
    keyLocation: SecuritySchemeIn,
    description: String = ApiKeySecurityScheme.DEFAULT_DESCRIPTION
) {
    val scheme = ApiKeySecurityScheme(name = keyName, `in` = keyLocation, description = description)
    registerSecurityScheme(name, scheme)
}

/**
 * Registers an OAuth2 authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param flows The OAuth2 flows configuration.
 * @param description Optional description for the security scheme. Defaults to "OAuth2 Authentication".
 */
public fun Application.registerOAuth2SecurityScheme(
    name: String? = null,
    flows: OAuthFlows,
    description: String? = OAuth2SecurityScheme.DEFAULT_DESCRIPTION
) {
    registerSecurityScheme(name, OAuth2SecurityScheme(flows, description))
}

/**
 * Registers an OpenID Connect authentication security scheme.
 *
 * @param name The name of the security scheme. Defaults to "default".
 * @param openIdConnectUrl The OpenID Connect discovery URL.
 * @param description Optional description for the security scheme. Defaults to "OpenID Connect Authentication".
 */
public fun Application.registerOpenIdConnectSecurityScheme(
    name: String? = null,
    openIdConnectUrl: String,
    description: String? = OpenIdConnectSecurityScheme.DEFAULT_DESCRIPTION
) {
    registerSecurityScheme(name, OpenIdConnectSecurityScheme(openIdConnectUrl, description))
}
