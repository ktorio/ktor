/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid.utils

import io.ktor.http.*
import io.ktor.server.auth.openid.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

internal val openIdProviderMetadata: OpenIdProviderMetadata = OpenIdProviderMetadata(
    issuer = ISSUER_URL,
    authorizationEndpoint = "$ISSUER_URL/authorize",
    tokenEndpoint = "$ISSUER_URL/token",
    jwksUri = "$ISSUER_URL/jwks",
)

internal fun metadataForIssuer(issuer: String): OpenIdProviderMetadata = OpenIdProviderMetadata(
    issuer = issuer,
    authorizationEndpoint = "$issuer/authorize",
    tokenEndpoint = "$issuer/token",
    jwksUri = "$issuer/jwks",
)

internal fun TestApplicationBuilder.openIdProvider(
    metadata: OpenIdProviderMetadata = openIdProviderMetadata
) {
    externalServices {
        hosts(ISSUER_URL) {
            routing {
                openIdDiscoveryEndpoint(metadata)
            }
        }
    }
}

internal fun TestApplicationBuilder.openIdProvider(
    issuer: String,
    metadata: OpenIdProviderMetadata = metadataForIssuer(issuer),
) {
    externalServices {
        hosts(issuer) {
            routing {
                openIdDiscoveryEndpoint(metadata)
            }
        }
    }
}

internal fun Route.openIdDiscoveryEndpoint(metadata: OpenIdProviderMetadata = openIdProviderMetadata) {
    get("/.well-known/openid-configuration") {
        call.respondText(openIdTestJson.encodeToString(metadata), ContentType.Application.Json)
    }
}
