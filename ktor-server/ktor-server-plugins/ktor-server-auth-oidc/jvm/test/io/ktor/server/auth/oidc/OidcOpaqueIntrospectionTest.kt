/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OidcOpaqueIntrospectionTest {

    @Test
    fun `opaque introspection validates active audience lifetime and issuer with basic auth`() = testApplication {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        openIdIntrospectionProvider { parameters ->
            assertNull(parameters["client_id"])
            assertNull(parameters["client_secret"])
            assertEquals("access_token", parameters["token_type_hint"])
            assertEquals(
                "Basic ${Base64.getEncoder().encodeToString("resource-server:secret".toByteArray())}",
                call.request.headers[HttpHeaders.Authorization],
            )
            when (parameters["token"]) {
                "active-token" -> """{"active":true,"sub":"opaque-user","aud":["api"],"scope":"read"}"""
                "primitive-audience-token" -> """{"active":true,"sub":"primitive-user","aud":"api","scope":"read"}"""
                "inactive-token" -> """{"active":false,"aud":["api"]}"""
                "wrong-audience-token" -> """{"active":true,"sub":"opaque-user","aud":["other-api"]}"""
                "missing-audience-token" -> """{"active":true,"sub":"opaque-user"}"""
                "expired-token" -> """{"active":true,"aud":["api"],"exp":${now - 120}}"""
                "not-yet-valid-token" -> """{"active":true,"aud":["api"],"nbf":${now + 120}}"""
                "wrong-issuer-token" -> """{"active":true,"aud":["api"],"iss":"https://issuer.example.net"}"""
                else -> """{"active":false}"""
            }
        }

        installOpaqueBearer("api")

        assertOpaqueToken("active-token", HttpStatusCode.OK, "opaque-user")
        assertOpaqueToken("primitive-audience-token", HttpStatusCode.OK, "primitive-user")
        for (token in listOf(
            "inactive-token",
            "wrong-audience-token",
            "missing-audience-token",
            "expired-token",
            "not-yet-valid-token",
            "wrong-issuer-token",
        )) {
            assertOpaqueToken(token, HttpStatusCode.Unauthorized)
        }
    }

    @Test
    fun `opaque introspection post auth treats primitive audience as a single value`() = testApplication {
        openIdIntrospectionProvider { parameters ->
            assertEquals("resource-server", parameters["client_id"])
            assertEquals("secret", parameters["client_secret"])
            assertEquals("access_token", parameters["token_type_hint"])
            assertNull(call.request.headers[HttpHeaders.Authorization])
            when (parameters["token"]) {
                "spaced-audience-token" -> """{"active":true,"sub":"primitive-user","aud":"my api","scope":"read"}"""
                else -> """{"active":false}"""
            }
        }

        installOpaqueBearer(
            audience = "my api",
            authMethod = OpaqueTokenIntrospectionAuthMethod.ClientSecretPost,
        )

        assertOpaqueToken("spaced-audience-token", HttpStatusCode.OK, "primitive-user")
    }

    private fun TestApplicationBuilder.openIdIntrospectionProvider(
        responseForToken: suspend RoutingContext.(Parameters) -> String,
    ) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    post("/introspect") {
                        call.respondText(responseForToken(call.receiveParameters()), ContentType.Application.Json)
                    }
                }
            }
        }
    }

    private fun ApplicationTestBuilder.installOpaqueBearer(
        audience: String,
        authMethod: OpaqueTokenIntrospectionAuthMethod = OpaqueTokenIntrospectionAuthMethod.ClientSecretBasic,
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
            }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf(audience)
                    opaqueToken = OpaqueTokenStrategy.Introspect(
                        endpoint = "$ISSUER_URL/introspect",
                        clientId = "resource-server",
                        clientSecret = "secret",
                        authMethod = authMethod,
                    )
                }
                bearer()
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/opaque") {
                        val opaque = principal as OidcToken.Opaque
                        call.respondText(opaque.introspection.subject ?: "missing")
                    }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.assertOpaqueToken(
        token: String,
        status: HttpStatusCode,
        body: String? = null,
    ) {
        val response = client.get("/opaque") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(status, response.status)
        body?.let { assertEquals(it, response.bodyAsText()) }
    }
}
