/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc.utils

import io.ktor.http.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal val openIdProviderMetadata: OpenIdProviderMetadata = OpenIdProviderMetadata(
    issuer = ISSUER_URL,
    authorizationEndpoint = "$ISSUER_URL/authorize",
    tokenEndpoint = "$ISSUER_URL/token",
    jwksUri = "$ISSUER_URL/jwks"
)

internal fun browserFlowMetadata(
    endSessionEndpoint: String? = "$ISSUER_URL/logout",
): OpenIdProviderMetadata = OpenIdProviderMetadata(
    issuer = ISSUER_URL,
    authorizationEndpoint = "$ISSUER_URL/authorize",
    tokenEndpoint = "$ISSUER_URL/token",
    userInfoEndpoint = "$ISSUER_URL/userinfo",
    jwksUri = "$ISSUER_URL/jwks",
    endSessionEndpoint = endSessionEndpoint,
)

internal fun <P : Any> OidcProviderConfig<P>.testIssuer(
    issuer: String = ISSUER_URL,
    metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(issuer),
) {
    this.issuer = issuer
    this.metadata = metadata
}

internal fun TestApplicationBuilder.openIdProvider(
    keys: OpenIdTestKeys,
    idTokensByState: Map<String, String>,
    tokenType: String? = "Bearer",
) {
    externalServices {
        hosts(ISSUER_URL) {
            routing {
                post("/token") {
                    val parameters = call.receiveParameters()
                    respondAuthorizationCodeWithIdToken(
                        parameters = parameters,
                        idTokensByState = idTokensByState,
                        accessToken = keys.accessToken {
                            subject = "token-user"
                        },
                        tokenType = tokenType,
                    )
                }
            }
        }
    }
}

internal suspend fun RoutingContext.respondAuthorizationCodeWithIdToken(
    parameters: Parameters,
    idTokensByState: Map<String, String>,
    accessToken: String,
    refreshToken: String? = "refresh-token-1",
    tokenType: String? = "Bearer",
) {
    assertAuthorizationCodeRequest(parameters)
    val state = assertNotNull(parameters["state"])

    val responseParameters = buildList {
        add("access_token" to accessToken)
        tokenType?.let { add("token_type" to it) }
        add("expires_in" to "3600")
        refreshToken?.let { add("refresh_token" to it) }
        add("id_token" to assertNotNull(idTokensByState[state]))
    }
    call.respondText(responseParameters.formUrlEncode(), ContentType.Application.FormUrlEncoded)
}

internal fun assertAuthorizationCodeRequest(parameters: Parameters) {
    assertEquals("authorization_code", parameters["grant_type"])
    assertEquals("login-code", parameters["code"])
    assertEquals("client-id", parameters["client_id"])
    assertEquals("client-secret", parameters["client_secret"])
    assertNotNull(parameters["state"])
}
