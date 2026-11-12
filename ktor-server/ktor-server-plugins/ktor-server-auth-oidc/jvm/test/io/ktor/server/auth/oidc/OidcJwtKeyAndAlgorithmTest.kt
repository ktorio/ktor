/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OidcJwtKeyAndAlgorithmTest {

    @Test
    fun `bearer authentication accepts JWT without kid when JWKS key verifies`() = testApplication {
        val keys = testRsaKeys
        installJwtBearer(keys = keys)

        val token = keys.accessToken {
            subject = "missing-kid"
            keyId = null
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("missing-kid", response.bodyAsText())
    }

    @Test
    fun `bearer authentication rejects JWT without kid when JWKS has multiple keys`() = testApplication {
        val keys = testRsaKeys

        installJwtBearer(jwkProviderFactory = { jwkProviderWithMultipleKeys(keys, testOtherRsaKeys) })

        val token = keys.accessToken {
            subject = "missing-kid"
            keyId = null
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `bearer authentication rejects JWT when JWK operations do not allow verification`() = testApplication {
        val keys = testRsaKeys

        installJwtBearer(jwkProviderFactory = { jwkProviderWithoutVerifyOperation(keys) })

        val token = keys.accessToken {
            subject = "key-ops"
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `access token algorithms ignore discovery id token allow list by default`() = testApplication {
        val keys = testRsaKeys
        installJwtBearer(
            keys = keys,
            metadata = OpenIdProviderMetadata(
                issuer = ISSUER_URL,
                authorizationEndpoint = "$ISSUER_URL/authorize",
                tokenEndpoint = "$ISSUER_URL/token",
                jwksUri = "$ISSUER_URL/jwks",
                idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_384.testJwaName),
            ),
        )

        val token = keys.accessToken { subject = "alg-user" }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `access token algorithms honor explicit allow list`() = testApplication {
        val keys = testRsaKeys

        installJwtBearer(
            keys = keys,
            configureJwt = { allowedAlgorithms = setOf(SignatureAlgorithm.RSA_SHA_384) },
        )

        val token = keys.accessToken {
            subject = "alg-user"
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `openId test keys issue RSA and EC access tokens with static metadata`() = testApplication {
        val rsaKeys = testRsaKeys
        val ecKeys = testEcKeys

        application {
            val oidc = install(Oidc)
            val rsaProvider = oidc.identityProvider("rsa") {
                testIssuer()
                jwt(rsaKeys)
                bearer { audience = setOf("api") }
            }
            val ecProvider = oidc.identityProvider("ec") {
                testIssuer("https://ec.example.com")
                jwt(ecKeys)
                bearer { audience = setOf("api") }
            }

            routing {
                authenticateWith(rsaProvider.jwtBearer) {
                    get("/rsa") {
                        call.respondText(call.principal.userInfo?.subject ?: "missing")
                    }
                }
                authenticateWith(ecProvider.jwtBearer) {
                    get("/ec") {
                        call.respondText(call.principal.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val rsaToken = rsaKeys.accessToken {
            subject = "rsa-user"
        }
        val rsa = client.get("/rsa") {
            header(
                HttpHeaders.Authorization,
                "Bearer $rsaToken"
            )
        }
        assertEquals(HttpStatusCode.OK, rsa.status)
        assertEquals("rsa-user", rsa.bodyAsText())

        val ecToken = ecKeys.accessToken {
            issuer = "https://ec.example.com"
            subject = "ec-user"
        }
        val ec = client.get("/ec") {
            header(HttpHeaders.Authorization, "Bearer $ecToken")
        }
        assertEquals(HttpStatusCode.OK, ec.status)
        assertEquals("ec-user", ec.bodyAsText())

        val ecOnRsaToken = ecKeys.accessToken {
            subject = "ec-on-rsa"
        }
        val ecTokenOnRsaProvider = client.get("/rsa") {
            header(HttpHeaders.Authorization, "Bearer $ecOnRsaToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, ecTokenOnRsaProvider.status)
    }

    @Test
    fun `access token builder preserves user info and custom claims`() = testApplication {
        val keys = testRsaKeys

        application {
            val oidc = install(Oidc)
            val provider = oidc.identityProvider("auth0") {
                testIssuer()
                jwt(keys)
                bearer { audience = setOf("api") }
            }

            routing {
                authenticateWith(provider.jwtBearer) {
                    get("/claims") {
                        val accessToken = call.principal
                        val roles = accessToken.claims.claim("realm_access")
                            ?.jsonObject
                            ?.get("roles")
                            ?.jsonArray
                            ?.map { it.jsonPrimitive.content }
                            ?: return@get call.respondText("No roles found")
                        val text = listOf(
                            accessToken.userInfo?.subject,
                            accessToken.userInfo?.email,
                            accessToken.userInfo?.name,
                            roles.joinToString(","),
                        ).joinToString(":")
                        call.respondText(text)
                    }
                }
            }
        }

        val token = keys.accessToken {
            subject = "user-1"
            email = "user@example.com"
            name = "Test User"
            claim("realm_access", mapOf("roles" to listOf("admin", "editor")))
        }

        val response = client.get("/claims") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user-1:user@example.com:Test User:admin,editor", response.bodyAsText())
    }

    @Test
    fun `id token builder supports nonce and access token hash`() = testApplication {
        val keys = testRsaKeys
        val accessToken = "access-token"

        lateinit var provider: OidcProvider
        application {
            val oidc = install(Oidc) { }
            provider = oidc.identityProvider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                get("/verify") {
                    provider.withCapturedState {
                        val principal = provider.buildIdToken(
                            idToken = keys.idToken(subject = "id-user") {
                                audience = "client-id"
                                nonce = "nonce-1"
                                atHash = keys.algorithm.hashAccessToken(accessToken)
                            },
                            accessToken = accessToken,
                            refreshToken = null,
                            expectedAudience = "client-id",
                            expectedNonce = "nonce-1",
                        )
                        call.respondText(principal.userInfo.subject)
                    }
                }
            }
        }

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("id-user", response.bodyAsText())
    }

    @Test
    fun `id token algorithms default to discovery metadata`() = testApplication {
        val keys = testRsaKeys
        val metadata = OpenIdProviderMetadata(
            issuer = ISSUER_URL,
            authorizationEndpoint = "$ISSUER_URL/authorize",
            tokenEndpoint = "$ISSUER_URL/token",
            jwksUri = "$ISSUER_URL/jwks",
            idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_384.testJwaName),
        )

        application {
            val oidc = install(Oidc) { }
            val provider = oidc.identityProvider("auth0") {
                testIssuer(metadata = metadata)
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                get("/verify") {
                    val failure = runCatching {
                        provider.withCapturedState {
                            provider.buildIdToken(
                                idToken = keys.idToken(subject = "alg-user") {
                                    audience = "client-id"
                                },
                                accessToken = "",
                                refreshToken = null,
                                expectedAudience = "client-id",
                            )
                        }
                    }.exceptionOrNull()
                    call.respondText(failure?.message.orEmpty())
                }
            }
        }

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "allowed algorithms")
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication accepts EdDSA access token`() = testApplication {
        val keys = testEdDsaKeys
        installJwtBearer(keys = keys)

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "eddsa-user" }}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("eddsa-user", response.bodyAsText())
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication accepts Ed25519 access token`() = testApplication {
        val keys = testEd25519Keys
        installJwtBearer(keys = keys)

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "ed25519-user" }}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ed25519-user", response.bodyAsText())
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_11, disabledReason = "RSASSA-PSS is available since JDK 11")
    fun `bearer authentication accepts RSASSA-PSS access tokens`() = testApplication {
        val keysByAlgorithm = testRsaPssKeysByAlgorithm
        installJwtBearer(jwkProviderFactory = { jwkProviderWithMultipleKeys(*keysByAlgorithm.values.toTypedArray()) })

        for ((algorithm, keys) in keysByAlgorithm) {
            val response = client.get("/protected") {
                header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = algorithm.testJwaName }}")
            }
            assertEquals(HttpStatusCode.OK, response.status, "${algorithm.name} token should be accepted")
            assertEquals(algorithm.testJwaName, response.bodyAsText())
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication rejects EdDSA token when OKP JWK curve is wrong or missing`() {
        for (curve in listOf("Ed448", null)) {
            testApplication {
                val keys = testEdDsaKeys
                installJwtBearer(jwkProviderFactory = { jwkProviderWithModifiedJwk(keys) { this["crv"] = curve } })

                val response = client.get("/protected") {
                    header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "eddsa-user" }}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status, "crv=$curve should be rejected")
            }
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication rejects EdDSA token when OKP JWK key has wrong length`() = testApplication {
        val keys = testEdDsaKeys
        installJwtBearer(jwkProviderFactory = { jwkProviderWithModifiedJwk(keys) { this["x"] = "AAAA" } })

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "eddsa-user" }}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication rejects EdDSA token signed by a different key`() = testApplication {
        val keys = testEdDsaKeys
        val otherKeys = OpenIdTestKeys.ed25519(issuer = ISSUER_URL, audience = "api")
        installJwtBearer(keys = keys)

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${otherKeys.accessToken { subject = "eddsa-user" }}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication rejects EdDSA token verified with RSA JWK`() = testApplication {
        val keys = testEdDsaKeys
        installJwtBearer(
            jwkProviderFactory = {
                jwkProviderWithModifiedJwk(testRsaKeys) { this["alg"] = SignatureAlgorithm.EDDSA.testJwaName }
            },
        )

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "eddsa-user" }}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `bearer authentication rejects Ed25519 token when JWK algorithm is EdDSA`() = testApplication {
        val keys = testEd25519Keys
        installJwtBearer(
            jwkProviderFactory = {
                jwkProviderWithModifiedJwk(keys) { this["alg"] = SignatureAlgorithm.EDDSA.testJwaName }
            },
        )

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.accessToken { subject = "ed25519-user" }}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `access token algorithms allow list accepts EdDSA and rejects others`() = testApplication {
        val edKeys = testEdDsaKeys
        installJwtBearer(
            jwkProviderFactory = { jwkProviderWithMultipleKeys(edKeys, testOtherRsaKeys) },
            configureJwt = { allowedAlgorithms = setOf(SignatureAlgorithm.EDDSA) },
        )

        val edDsa = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${edKeys.accessToken { subject = "eddsa-user" }}")
        }
        assertEquals(HttpStatusCode.OK, edDsa.status)

        val rsa = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${testOtherRsaKeys.accessToken { subject = "rsa-user" }}")
        }
        assertEquals(HttpStatusCode.Unauthorized, rsa.status)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `id token verification accepts EdDSA with access token hash`() = testApplication {
        val keys = testEdDsaKeys
        val accessToken = "access-token"
        installIdTokenVerification(
            jwtConfig = { jwt(keys) },
            idToken = {
                keys.idToken(subject = "eddsa-user") {
                    audience = "client-id"
                    atHash = keys.algorithm.hashAccessToken(accessToken)
                }
            },
            accessToken = accessToken,
        )

        assertEquals("eddsa-user", client.get("/verify").bodyAsText())
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `id token verification rejects EdDSA access token hash mismatch`() = testApplication {
        val keys = testEdDsaKeys
        installIdTokenVerification(
            jwtConfig = { jwt(keys) },
            idToken = {
                keys.idToken(subject = "eddsa-user") {
                    audience = "client-id"
                    atHash = keys.algorithm.hashAccessToken("other-access-token")
                }
            },
            accessToken = "access-token",
        )

        assertContains(client.get("/verify").bodyAsText(), "at_hash")
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `id token verification rejects EdDSA when discovery metadata does not advertise it`() = testApplication {
        val keys = testEdDsaKeys
        installIdTokenVerification(
            metadata = OpenIdProviderMetadata(
                issuer = ISSUER_URL,
                authorizationEndpoint = "$ISSUER_URL/authorize",
                tokenEndpoint = "$ISSUER_URL/token",
                jwksUri = "$ISSUER_URL/jwks",
                idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_256.testJwaName),
            ),
            jwtConfig = { jwt { jwkProviderFactory = { keys.jwkProvider } } },
            idToken = { keys.idToken(subject = "eddsa-user") { audience = "client-id" } },
        )

        assertContains(client.get("/verify").bodyAsText(), "allowed algorithms")
    }

    @Test
    fun `allowed algorithms must be available in the JVM`() {
        val unavailable = SignatureAlgorithm(
            name = "Unavailable",
            jcaAlgorithm = "NoSuchSignatureAlgorithm",
            digestAlgorithm = DigestAlgorithm.SHA_256,
            keyAlgorithm = KeyAlgorithm.OKP,
            jwaName = "Unavailable",
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(Oidc).identityProvider("auth0") {
                        testIssuer()
                        jwt { allowedAlgorithms = setOf(unavailable) }
                    }
                }
                startApplication()
            }
        }
        assertContains(failure.message.orEmpty(), "Unavailable")
        assertContains(failure.message.orEmpty(), "not available")
    }

    @Test
    fun `openId test keys reject non EdDSA algorithms for Ed25519 keys`() {
        assertFailsWith<IllegalArgumentException> {
            OpenIdTestKeys.ed25519(algorithm = SignatureAlgorithm.RSA_SHA_256)
        }
    }

    private fun ApplicationTestBuilder.installIdTokenVerification(
        metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(ISSUER_URL),
        jwtConfig: OidcProviderConfig.() -> Unit,
        idToken: () -> String,
        accessToken: String = "",
    ) {
        lateinit var provider: OidcProvider
        application {
            val oidc = install(Oidc) { }
            provider = oidc.identityProvider("auth0") {
                testIssuer(metadata = metadata)
                jwtConfig()
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                get("/verify") {
                    val result = runCatching {
                        provider.withCapturedState {
                            provider.buildIdToken(
                                idToken = idToken(),
                                accessToken = accessToken,
                                refreshToken = null,
                                expectedAudience = "client-id",
                            ).userInfo.subject
                        }
                    }
                    call.respondText(result.getOrElse { it.message.orEmpty() })
                }
            }
        }
    }

    private fun ApplicationTestBuilder.installJwtBearer(
        keys: OpenIdTestKeys? = null,
        jwkProviderFactory: ((String) -> com.auth0.jwk.JwkProvider)? = null,
        metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(ISSUER_URL),
        configureJwt: OidcJwtConfig.() -> Unit = {},
    ) {
        application {
            val oidc = install(Oidc)
            val provider = oidc.identityProvider("auth0") {
                testIssuer(metadata = metadata)
                if (jwkProviderFactory == null && keys != null) {
                    jwt(keys)
                }
                jwt {
                    configureJwt()
                    jwkProviderFactory?.let { this.jwkProviderFactory = it }
                }
                bearer { audience = setOf("api") }
            }

            routing {
                authenticateWith(provider.jwtBearer) {
                    get("/protected") {
                        call.respondText(call.principal.userInfo?.subject ?: "ok")
                    }
                }
            }
        }
    }
}
