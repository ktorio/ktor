/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

internal const val ISSUER_URL: String = "https://auth0.example.com"

internal val discoveryJson = Json { ignoreUnknownKeys = true }

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
