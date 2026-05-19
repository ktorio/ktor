/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json

internal const val ISSUER_URL: String = "https://auth0.example.com"

internal val openIdTestJson: Json = Json { ignoreUnknownKeys = true }

internal fun ApplicationTestBuilder.noRedirectsClient(): HttpClient = createClient { followRedirects = false }

internal fun ApplicationTestBuilder.openIdHttpClient(): HttpClient = createClient {
    install(ContentNegotiation) {
        json(openIdTestJson)
    }
}
