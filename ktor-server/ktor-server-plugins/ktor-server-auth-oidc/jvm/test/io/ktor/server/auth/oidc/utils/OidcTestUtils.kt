/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

internal const val ISSUER_URL: String = "https://auth0.example.com"

internal const val OIDC_TEST_SESSION_NAME: String = "OIDC_TEST_SESSION"

internal val discoveryJson = Json { ignoreUnknownKeys = true }

internal fun testOpenIdProviderMetadata(
    issuer: String,
    jwksUri: String = "$issuer/jwks",
    authorizationEndpoint: String = "$issuer/authorize",
    tokenEndpoint: String = "$issuer/token",
    userInfoEndpoint: String? = "$issuer/userinfo",
    endSessionEndpoint: String? = "$issuer/logout",
): OpenIdProviderMetadata =
    OpenIdProviderMetadata(
        issuer = issuer,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        userInfoEndpoint = userInfoEndpoint,
        jwksUri = jwksUri,
        endSessionEndpoint = endSessionEndpoint,
    )

internal fun ApplicationTestBuilder.noRedirectsClient(): HttpClient = createClient { followRedirects = false }

internal fun ApplicationTestBuilder.discoveryClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(discoveryJson)
    }
}

internal fun ApplicationTestBuilder.openIdHttpClient(): HttpClient = discoveryClient()

internal fun Application.installDiscoveryContentNegotiation() {
    install(ServerContentNegotiation) {
        json(discoveryJson)
    }
}

internal fun HttpResponse.oidcSessionCookieHeader(name: String = OIDC_TEST_SESSION_NAME): String? {
    return headers.getAll(HttpHeaders.SetCookie)
        .orEmpty()
        .firstOrNull { it.startsWith("$name=") }
        ?.let(::parseServerSetCookieHeader)
        ?.let { "${it.name}=${it.value}" }
}

internal fun HttpResponse.oidcStateCookieHeader(): String? {
    return headers.getAll(HttpHeaders.SetCookie)
        .orEmpty()
        .firstOrNull { it.startsWith("$OidcStateCookieName=") }
        ?.let(::parseServerSetCookieHeader)
        ?.let { "${it.name}=${it.value}" }
}
