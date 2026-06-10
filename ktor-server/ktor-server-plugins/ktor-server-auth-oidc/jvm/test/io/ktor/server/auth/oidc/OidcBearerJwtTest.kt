/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, ExperimentalTime::class)

package io.ktor.server.auth.oidc

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class OidcBearerJwtTest {

    @Test
    fun `bearer authentication rejects invalid JWT inputs`() = testApplication {
        val keys = OpenIdTestKeys()
        val otherKeys = OpenIdTestKeys()

        openIdProvider()
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("google") {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val validToken = keys.token(audience = "api", subject = "valid-user")
        val valid = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $validToken")
        }
        assertEquals(HttpStatusCode.OK, valid.status)
        assertEquals("valid-user", valid.bodyAsText())

        val expired = keys.token(
            audience = "api",
            subject = "expired-user",
            expiresAt = Date(Clock.System.now().minus(60.seconds).toEpochMilliseconds()),
        )
        val failures = listOf(
            null,
            "Basic $validToken",
            "Bearer not-a-jwt",
            "Bearer ${keys.hmacToken(audience = "api", subject = "hmac")}",
            "Bearer ${keys.unsignedToken(audience = "api", subject = "unsigned")}",
            "Bearer ${keys.token(audience = "api", subject = "wrong-issuer", issuer = "https://issuer.example.net")}",
            "Bearer ${otherKeys.token(audience = "api", subject = "wrong-signature", keyId = "kid-1")}",
            "Bearer ${keys.token(audience = "other-api", subject = "wrong-audience")}",
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
        val keys = OpenIdTestKeys()

        openIdProvider()
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("auth0") {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(
                            "${accessToken.userInfo?.subject ?: "missing"}:${accessToken.clientId ?: "missing"}"
                        )
                    }
                }
            }
        }

        val response = client.get("/protected") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${keys.token(audience = "api", subject = null, clientId = "service-client")}"
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("missing:service-client", response.bodyAsText())
    }

    @Test
    fun `token claims headerString preserves embedded quotes`() {
        val keys = OpenIdTestKeys()
        val token = keys.token(audience = "api", headerClaims = mapOf("quoted" to "a\"b"))
        val claims = TokenClaims(JWT.decode(token))

        assertEquals("a\"b", claims.headerString("quoted"))
    }

    @Test
    fun `principal serialization uses stable token serial names`() {
        val json = Json {
            serializersModule = OidcToken.serializersModule
        }
        val serializer = PolymorphicSerializer(OidcToken::class)

        val accessToken = json.encodeToString(serializer, OidcToken.Access("access-token"))
        val idToken = json.encodeToString(
            serializer,
            OidcToken.Id(
                value = "id-token",
                accessToken = "access-token",
                userInfo = OidcToken.UserInfo(subject = "user"),
            )
        )

        assertContains(accessToken, "\"type\":\"access_token\"")
        assertContains(idToken, "\"type\":\"id_token\"")
    }

    @Test
    fun `custom token source replaces authorization header`() = testApplication {
        val keys = OpenIdTestKeys()

        openIdProvider()
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("auth0") {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                accessToken {
                    audiences = setOf("custom-api")
                }
                bearer {
                    tokenExtractor = { call ->
                        call.request.headers["X-Api-Token"]
                    }
                }
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/custom") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val token = keys.token(audience = "custom-api", subject = "custom-user")
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
        val keys = OpenIdTestKeys()
        val malformedHeader = "Bearer invalid@" + "x".repeat(160)

        captureProviderLogs("auth0", ch.qos.logback.classic.Level.TRACE).use { logs ->
            openIdProvider()
            val openIdClient = openIdHttpClient()
            application {
                val oidc = openIdConnect {
                    httpClient = openIdClient
                    discoveryRefreshInterval = ZERO
                }
                val provider = oidc.provider("auth0") {
                    issuer = ISSUER_URL
                    jwt {
                        jwkProviderFactory = { keys.jwkProvider }
                    }
                    accessToken {
                        audiences = setOf("api")
                    }
                    bearer()
                }

                routing {
                    authenticateWith(provider.bearer) {
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
        openIdProvider()
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val provider = oidc.provider("auth0") {
                issuer = ISSUER_URL
                accessToken {
                    audiences = setOf("api")
                }
            }

            routing {
                get("/verify") {
                    val failure = try {
                        provider.verifyAccessToken("not-a-jwt-with-secret")
                        null
                    } catch (cause: Throwable) {
                        cause
                    }
                    assertIs<OidcTokenRejectedException>(failure)
                    call.respondText(failure.message.orEmpty())
                }
            }
        }

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("The token was expected to have 3 parts, but got 0.", response.bodyAsText())
    }

    @Test
    fun `transformPrincipal exposes typed application principal`() = testApplication {
        val keys = OpenIdTestKeys()

        openIdProvider()
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }

            val google = oidc.provider(
                name = "google",
                transformPrincipal = { p ->
                    val accessToken = p as? OidcToken.Access
                    accessToken?.userInfo?.subject?.let(::UserIdPrincipal)
                }
            ) {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(google.bearer) {
                    get("/typed") {
                        call.respondText(principal.name)
                    }
                }
            }
        }

        val response = client.get("/typed") {
            header(HttpHeaders.Authorization, "Bearer ${keys.token(audience = "api", subject = "typed-user")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("typed-user", response.bodyAsText())
    }
}
