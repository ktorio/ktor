/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.atomic.*
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcUserInfoTest {

    @Test
    fun `buildVerifiedPrincipal accepts signed userinfo and validates subject`() = testApplication {
        val keys = testRsaKeys
        val userInfoBody = AtomicReference(
            keys.idToken(subject = "userinfo-user") { audience = "client-id" }
        )
        val userInfoContentType = AtomicReference(ContentType("application", "jwt"))

        openIdUserInfoProvider(userInfoBody, userInfoContentType)
        installUserInfoVerifier(keys)

        val signed = client.get("/verify")
        assertEquals(HttpStatusCode.OK, signed.status)
        assertEquals("userinfo-user", signed.bodyAsText())

        userInfoBody.set("""{"sub":"other-user"}""")
        userInfoContentType.set(ContentType.Application.Json)
        assertUserInfoFailure("UserInfo subject mismatch")

        userInfoBody.set("{}")
        assertUserInfoFailure("sub")

        userInfoBody.set("a.b.c.d.e")
        userInfoContentType.set(ContentType("application", "jwt"))
        assertUserInfoFailure("Encrypted UserInfo JWT responses are not supported")
    }

    @Test
    fun `buildVerifiedPrincipal rejects invalid signed userinfo`() = testApplication {
        val keys = testRsaKeys
        val otherKeys = testOtherRsaKeys
        val otherAlgorithmKeys = testRsaKeysByAlgorithm.getValue(SignatureAlgorithm.RSA_SHA_384)
        val userInfoBody = AtomicReference(
            keys.idToken(subject = "userinfo-user") { audience = "client-id" }
        )
        val userInfoContentType = AtomicReference(ContentType("application", "jwt"))

        openIdUserInfoProvider(userInfoBody, userInfoContentType)
        installUserInfoVerifier(keys, idTokenAudience = "api-audience")

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("userinfo-user", response.bodyAsText())

        val invalidCases = listOf(
            otherKeys.idToken(subject = "userinfo-user") {
                audience = "client-id"
                keyId = keys.keyId
            } to "signature",
            keys.idToken(subject = "userinfo-user") {
                issuer = "https://issuer.example.net"
                audience = "client-id"
            } to "issuer",
            keys.idToken(subject = "userinfo-user") {
                audience = "other-client"
            } to "audience",
            keys.idToken(subject = "other-user") {
                audience = "client-id"
            } to "UserInfo subject mismatch",
            otherAlgorithmKeys.idToken(subject = "userinfo-user") {
                audience = "client-id"
            } to "allowed algorithms",
        )

        for ((token, expectedMessage) in invalidCases) {
            userInfoBody.set(token)
            assertUserInfoFailure(expectedMessage)
        }
    }

    private fun ApplicationTestBuilder.installUserInfoVerifier(
        keys: OpenIdTestKeys,
        idTokenAudience: String? = null,
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer(
                    metadata = OpenIdProviderMetadata(
                        issuer = ISSUER_URL,
                        authorizationEndpoint = "$ISSUER_URL/authorize",
                        tokenEndpoint = "$ISSUER_URL/token",
                        userInfoEndpoint = "$ISSUER_URL/userinfo",
                        jwksUri = "$ISSUER_URL/jwks",
                        userinfoSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_256.testJwaName),
                    )
                )
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    idTokenAudience?.let { this.idTokenAudience = it }
                    fetchUserInfo = true
                }
            }
            val expectedIdTokenAudience = idTokenAudience ?: "client-id"
            routing {
                get("/verify") {
                    val result = runCatching {
                        oidcProvider.buildIdToken(
                            idToken = keys.idToken(subject = "userinfo-user") {
                                audience = expectedIdTokenAudience
                            },
                            accessToken = "access-token",
                            refreshToken = null,
                            expectedAudience = expectedIdTokenAudience,
                            fetchUserInfo = true,
                        )
                    }
                    val principal = result.getOrNull()
                    call.respondText(principal?.userInfo?.subject ?: result.exceptionOrNull()?.message.orEmpty())
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.assertUserInfoFailure(expectedMessage: String) {
        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText().lowercase(), expectedMessage.lowercase())
    }

    private fun TestApplicationBuilder.openIdUserInfoProvider(
        userInfoBody: AtomicReference<String>,
        userInfoContentType: AtomicReference<ContentType>,
    ) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/userinfo") {
                        assertEquals("Bearer access-token", call.request.headers[HttpHeaders.Authorization])
                        call.respondText(userInfoBody.get(), userInfoContentType.get())
                    }
                }
            }
        }
    }
}
