/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class OidcBearerJwtTest {

    @Test
    fun `bearer authentication rejects invalid JWT inputs`() = testApplication {
        val keys = testRsaKeys
        val otherKeys = testOtherRsaKeys

        application {
            val oidc = install(Oidc)
            val oidcProvider = oidc.identityProvider("google") {
                testIssuer()
                jwt(keys)
                bearer {
                    audience = setOf("api")
                }
            }

            routing {
                authenticateWith(oidcProvider.jwtBearer) {
                    get("/protected") {
                        call.respondText(call.principal.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val validToken = keys.accessToken {
            subject = "valid-user"
        }
        val valid = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $validToken")
        }
        assertEquals(HttpStatusCode.OK, valid.status)
        assertEquals("valid-user", valid.bodyAsText())

        val expired = keys.accessToken {
            subject = "expired-user"
            expiresAt = Clock.System.now() - 60.seconds
        }
        val wrongIssuer = keys.accessToken {
            issuer = "https://issuer.example.net"
            subject = "wrong-issuer"
        }
        val wrongSignature = otherKeys.accessToken {
            subject = "wrong-signature"
            keyId = "kid-1"
        }
        val wrongAudience = keys.accessToken {
            audience = "other-api"
            subject = "wrong-audience"
        }
        val failures = listOf(
            null,
            "Basic $validToken",
            "Bearer not-a-jwt",
            "Bearer ${hmacToken(audience = "api", subject = "hmac")}",
            "Bearer ${unsignedToken(audience = "api", subject = "unsigned")}",
            "Bearer $wrongIssuer",
            "Bearer $wrongSignature",
            "Bearer $wrongAudience",
            "Bearer $expired",
        )

        for (authorizationHeader in failures) {
            val response = client.get("/protected") {
                authorizationHeader?.let { header(HttpHeaders.Authorization, it) }
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status, "header=$authorizationHeader")
        }
    }

    @Test
    fun `bearer authentication accepts JWT access token without subject`() = testApplication {
        val keys = testRsaKeys

        application {
            val oidc = install(Oidc)
            val oidcProvider = oidc.identityProvider("auth0") {
                testIssuer()
                jwt(keys)
                bearer { audience = setOf("api") }
            }

            routing {
                authenticateWith(oidcProvider.jwtBearer) {
                    get("/protected") {
                        val accessToken = call.principal
                        val text = "${accessToken.userInfo?.subject ?: "missing"}:${accessToken.clientId ?: "missing"}"
                        call.respondText(text)
                    }
                }
            }
        }

        val token = keys.accessToken {
            clientId = "service-client"
            claim("azp", "authorized-party")
        }
        val response = client.get("/protected") {
            header(
                HttpHeaders.Authorization,
                "Bearer $token"
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("missing:authorized-party", response.bodyAsText())
    }

    @Test
    fun `token claims headerString preserves embedded quotes`() {
        val token = testRsaKeys.accessToken { keyId = "a\"b" }
        val claims = TokenClaims(JWT.decode(token))

        assertEquals("a\"b", claims.headerString("kid"))
    }

    @Test
    fun `token claims decode url-safe base64 payload segments`() {
        // A "~~~" byte run always yields a URL-safe-only character (- or _) in the encoded segment.
        val token = testRsaKeys.accessToken {
            subject = "claims-user"
            claim("mark", "~~~")
        }
        val claims = TokenClaims(JWT.decode(token))

        assertTrue(token.contains('-') || token.contains('_'))
        assertEquals("claims-user", claims.payload["sub"]?.jsonPrimitive?.content)
        assertEquals("~~~", claims.claimString("mark"))
    }

    @Test
    fun `token claims string accessors are null only for absent values`() {
        val token = testRsaKeys.accessToken {
            subject = "claims-user"
            claim("roles", listOf("admin", "user"))
            claim("count", 42)
        }
        val claims = TokenClaims(JWT.decode(token))

        assertNull(claims.claimString("missing"))
        assertFailsWith<IllegalArgumentException> { claims.claimString("roles") }
        assertFailsWith<IllegalArgumentException> { claims.claimString("count") }
        assertNull(claims.headerString("missing"))
    }

    @Test
    fun `custom token source replaces authorization header`() = testApplication {
        application {
            val oidc = install(Oidc)
            val oidcProvider = oidc.identityProvider("auth0") {
                testIssuer()
                jwt(testRsaKeys)
                bearer {
                    audience = setOf("custom-api")
                    tokenExtractor = { call.request.headers["X-Api-Token"] }
                }
            }

            routing {
                authenticateWith(oidcProvider.jwtBearer) {
                    get("/custom") {
                        val accessToken = call.principal
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val token = testRsaKeys.accessToken {
            audience = "custom-api"
            subject = "custom-user"
        }
        val authorizationHeaderIgnored = client.get("/custom") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, authorizationHeaderIgnored.status)

        val customHeaderAccepted = client.get("/custom") {
            header("X-Api-Token", token)
        }
        assertEquals(HttpStatusCode.OK, customHeaderAccepted.status)
        assertEquals("custom-user", customHeaderAccepted.bodyAsText())
    }

    @Test
    fun `malformed authorization header is logged at trace level with truncated value`() = testApplication {
        val malformedHeader = "Bearer invalid@" + "x".repeat(160)

        captureProviderLogs("auth0", ch.qos.logback.classic.Level.TRACE).use { logs ->
            application {
                val oidc = install(Oidc)
                val provider = oidc.identityProvider("auth0") {
                    testIssuer()
                    jwt(testRsaKeys)
                    bearer {
                        audience = setOf("api")
                    }
                }

                routing {
                    authenticateWith(provider.jwtBearer) {
                        get("/protected") {
                            call.respondText("ok")
                        }
                    }
                }
            }

            val response = client.get("/protected") {
                header(HttpHeaders.Authorization, malformedHeader)
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val logEvent = assertNotNull(
                logs.events.firstOrNull {
                    it.formattedMessage.contains("Malformed OpenID Connect Authorization header ignored")
                }
            )
            assertContains(logEvent.formattedMessage, "Bearer invalid@")
            assertContains(logEvent.formattedMessage, "...")
            assertTrue(!logEvent.formattedMessage.contains("x".repeat(120)))
        }
    }

    @Test
    fun `verifyAccessToken normalizes malformed jwt rejection`() = testApplication {
        application {
            val oidc = install(Oidc)
            val provider = oidc.identityProvider("auth0") {
                testIssuer()
                bearer { audience = setOf("api") }
            }

            assertFailsWith<OidcTokenRejectedException> {
                provider.verifyJwtAccessToken("not-a-jwt-with-secret")
            }
        }
    }

    @Test
    fun `jwt bearer rejects id tokens by token_use and typ`() = testApplication {
        application {
            val keys = testRsaKeys
            val provider = install(Oidc).identityProvider("google") {
                testIssuer()
                jwt(keys)
                bearer { audience = setOf("api") }
            }

            val accepted = listOf(
                keys.accessToken { subject = "plain-access" },
                keys.accessTokenWithPurpose(tokenUse = "access_token"),
                keys.accessTokenWithPurpose(typ = "at+jwt"),
                keys.accessTokenWithPurpose(typ = "application/at+jwt"),
                keys.accessTokenWithPurpose(tokenUse = "access_token", typ = "JWT"),
            )
            for (token in accepted) {
                assertEquals(token, provider.verifyJwtAccessToken(token).value)
            }

            val rejected = listOf(
                keys.accessTokenWithPurpose(tokenUse = "id_token"),
                keys.accessTokenWithPurpose(typ = "id_token"),
                keys.accessTokenWithPurpose(tokenUse = "id_token", typ = "JWT"),
            )

            for (token in rejected) {
                assertFailsWith<OidcTokenRejectedException> {
                    provider.verifyJwtAccessToken(token)
                }
            }
        }
    }
}
