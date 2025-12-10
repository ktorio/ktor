/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.openapi.*
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.SessionProvidersKey
import io.ktor.server.sessions.SessionTransportCookie
import io.ktor.server.sessions.SessionTransportHeader
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Attribute key for storing OpenAPI security scheme metadata for authentication providers.
 * Maps provider names to their corresponding SecurityScheme definitions.
 */
public val AuthenticationSecuritySchemesAttributeKey: AttributeKey<Map<String, SecurityScheme>> =
    AttributeKey("AuthenticationSecuritySchemes")

/**
 * Registers a security scheme for an authentication provider.
 * This metadata will be used to generate the OpenAPI specification.
 *
 * @param providerName The name of the authentication provider. Defaults to "default".
 * @param securityScheme The OpenAPI security scheme definition.
 */
public fun Application.registerSecurityScheme(
    providerName: String?,
    securityScheme: SecurityScheme
) {
    val providerName = providerName ?: AuthenticationRouteSelector.DEFAULT_NAME
    val existingSchemes = attributes.getOrNull(AuthenticationSecuritySchemesAttributeKey) ?: emptyMap()
    attributes.put(AuthenticationSecuritySchemesAttributeKey, existingSchemes + (providerName to securityScheme))
}

/**
 * Retrieves all registered security schemes from the application.
 *
 * @param inferFromAuthenticationPlugin Whether to infer security schemes from the installed [Authentication] plugin.
 *
 * @return A map of provider names to their security schemes, or null if no schemes are available.
 */
public fun Application.findSecuritySchemes(
    inferFromAuthenticationPlugin: Boolean = true
): Map<String, ReferenceOr<SecurityScheme>>? {
    val manualSchemes = attributes.getOrNull(AuthenticationSecuritySchemesAttributeKey)
    val inferredSchemes = when (inferFromAuthenticationPlugin) {
        true -> inferSecuritySchemesFromAuthentication()
        false -> null
    }
    val manualSchemesRefs = manualSchemes?.wrap()
    val inferredSchemesRefs = inferredSchemes?.wrap()
    return when {
        manualSchemesRefs != null && inferredSchemesRefs != null -> inferredSchemesRefs + manualSchemesRefs
        manualSchemesRefs != null -> manualSchemesRefs
        inferredSchemesRefs != null -> inferredSchemesRefs
        else -> null
    }
}

private fun Map<String, SecurityScheme>.wrap() = buildMap<String, ReferenceOr<SecurityScheme>> {
    for ((providerName, securityScheme) in this@wrap) {
        set(providerName, value(securityScheme))
    }
}

@OptIn(InternalAPI::class)
private fun Application.inferSecuritySchemesFromAuthentication(): Map<String, SecurityScheme>? {
    val authPlugin = pluginOrNull(Authentication) ?: return null
    val providers = authPlugin.configuration().allProviders()
    return buildMap {
        for ((providerName, provider) in providers) {
            inferSecurityScheme(provider)?.let {
                set(providerName ?: AuthenticationRouteSelector.DEFAULT_NAME, it)
            }
        }
    }.ifEmpty { null }
}

/**
 * Infers the OpenAPI security scheme from an authentication provider based on its type.
 */
@OptIn(InternalAPI::class)
private fun Application.inferSecurityScheme(provider: AuthenticationProvider): SecurityScheme? {
    inferPlatformSpecificSecurityScheme(provider)?.let { return it }

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
                `in` = keyLocation,
                name = keyName,
                description = provider.description
                    ?: ApiKeySecurityScheme.defaultDescription(keyName, keyLocation.name.lowercase())
            )
        }

        is FormAuthenticationProvider -> HttpSecurityScheme(
            scheme = "form",
            description = provider.description ?: (
                "Form-based Authentication with post parameters " +
                    "${provider.userParamName} and ${provider.passwordParamName}"
                )
        )

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

        else -> null
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
 * @param location The location of the API key (header, query, or cookie).
 * @param description Optional description for the security scheme. Defaults to "API Key Authentication".
 */
public fun Application.registerApiKeySecurityScheme(
    name: String?,
    keyName: String,
    location: SecuritySchemeIn,
    description: String = ApiKeySecurityScheme.defaultDescription(keyName, location.name)
) {
    val scheme = ApiKeySecurityScheme(name = keyName, `in` = location, description = description)
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
    name: String?,
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
    name: String?,
    openIdConnectUrl: String,
    description: String? = OpenIdConnectSecurityScheme.DEFAULT_DESCRIPTION
) {
    registerSecurityScheme(name, OpenIdConnectSecurityScheme(openIdConnectUrl, description))
}
