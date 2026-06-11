/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.net.URI

internal fun Application.configureProtectedResourceRoute(
    config: ProtectedResourceMetadataConfig,
    providers: () -> List<OidcProviderConfig<*>>,
) = routing {
    install(ContentNegotiation) {
        val format = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        json(format)
    }

    val metadata by lazy { buildProtectedResourceMetadata(config, providers()) }
    val path = buildResourceMetadataRoutePath(config.resource)
    get(path) { call.respond(metadata) }
}

internal fun buildProtectedResourceMetadata(
    config: ProtectedResourceMetadataConfig,
    providers: Collection<OidcProviderConfig<*>>,
): ProtectedResourceMetadata {
    val authorizationServers = config.authorizationServers
        ?: providers.map { it.issuer }.distinct().ifEmpty { null }

    val scopesSupported = config.scopesSupported
        ?: providers
            .mapNotNull { it.oauthConfig?.scopes }
            .flatten()
            .distinct()
            .ifEmpty { null }

    val bearerMethodsSupported = config.bearerMethodsSupported
        ?: listOf("header").takeIf {
            providers.any { provider ->
                val bearerConfig = provider.bearerConfig ?: return@any false
                bearerConfig.tokenExtractor == null
            }
        }

    return ProtectedResourceMetadata(
        resource = config.resource,
        authorizationServers = authorizationServers,
        jwksUri = config.jwksUri,
        scopesSupported = scopesSupported,
        bearerMethodsSupported = bearerMethodsSupported,
        resourceSigningAlgValuesSupported = config.resourceSigningAlgValuesSupported,
        resourceName = config.resourceName,
        resourceDocumentation = config.resourceDocumentation,
        resourcePolicyUri = config.resourcePolicyUri,
        resourceTosUri = config.resourceTosUri,
        tlsClientCertificateBoundAccessTokens = config.tlsClientCertificateBoundAccessTokens,
        authorizationDetailsTypesSupported = config.authorizationDetailsTypesSupported,
        dpopSigningAlgValuesSupported = config.dpopSigningAlgValuesSupported,
        dpopBoundAccessTokensRequired = config.dpopBoundAccessTokensRequired,
    )
}

internal fun buildResourceMetadataUrl(resource: String): String {
    val uri = parseProtectedResourceUri(resource)
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val resourcePath = uri.path?.trimEnd('/') ?: ""
    return "${uri.scheme}://${uri.host}$port/.well-known/oauth-protected-resource$resourcePath"
}

private fun buildResourceMetadataRoutePath(resource: String): String {
    val resourcePath = parseProtectedResourceUri(resource).path?.trimEnd('/') ?: ""
    return "/.well-known/oauth-protected-resource$resourcePath"
}

private fun parseProtectedResourceUri(resource: String): URI = URI(resource).apply {
    require(scheme?.equals("https", ignoreCase = true) == true) {
        "protectedResource(resource) must use the https scheme: $resource"
    }
    require(!host.isNullOrBlank()) { "protectedResource(resource) must include a host: $resource" }
    require(rawUserInfo == null) { "protectedResource(resource) must not include userinfo: $resource" }
    require(rawQuery == null) { "protectedResource(resource) must not include a query: $resource" }
    require(rawFragment == null) { "protectedResource(resource) must not include a fragment: $resource" }
}
