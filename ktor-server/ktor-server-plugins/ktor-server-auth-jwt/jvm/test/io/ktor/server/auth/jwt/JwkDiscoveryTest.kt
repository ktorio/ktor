/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class JwksKey(
    @SerialName("kid") val keyId: String?,
    @SerialName("kty") val keyType: String?,
    @SerialName("alg") val algorithm: String?,
    @SerialName("use") val usage: String?,
    @SerialName("n") val modulus: String?,
    @SerialName("e") val exponent: String?
)

@Serializable
internal data class JwksKeys(val keys: List<JwksKey>)

class JwkDiscoveryTest {
    private val discoveryJson = Json { ignoreUnknownKeys = true }

    private fun String.toConfigUrl() = "$this/.well-known/openid-configuration"

    @Test
    fun testFetchOpenIdConfigurationWithMockProviders() = testApplication {
        val googleIssuer = "https://accounts.google.com"
        val auth0Issuer = "https://example.auth0.com"
        val keycloakIssuer = "https://keycloak.example/realms/demo"

        val responses = mapOf(
            googleIssuer.toConfigUrl() to OpenIdConfiguration(
                googleIssuer,
                jwksUri = "https://www.googleapis.com/oauth2/v3/certs"
            ),
            auth0Issuer.toConfigUrl() to OpenIdConfiguration(
                auth0Issuer,
                jwksUri = "https://example.auth0.com/.well-known/jwks.json"
            ),
            keycloakIssuer.toConfigUrl() to OpenIdConfiguration(
                keycloakIssuer,
                jwksUri = "https://keycloak.example/realms/demo/protocol/openid-connect/certs"
            )
        ).mapValues { discoveryJson.encodeToString(it.value) }

        val engine = MockEngine { request ->
            val responseBody =
                responses[request.url.toString()] ?: return@MockEngine respondError(HttpStatusCode.NotFound)
            respond(
                content = responseBody,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)

        val googleConfig = client.fetchOpenIdConfiguration(googleIssuer)
        assertEquals("https://www.googleapis.com/oauth2/v3/certs", googleConfig.jwksUri)

        val auth0Config = client.fetchOpenIdConfiguration(auth0Issuer)
        assertEquals("https://example.auth0.com/.well-known/jwks.json", auth0Config.jwksUri)

        val keycloakConfig = client.fetchOpenIdConfiguration(keycloakIssuer)
        assertEquals(
            "https://keycloak.example/realms/demo/protocol/openid-connect/certs",
            keycloakConfig.jwksUri
        )
    }

    @Test
    fun testFetchOpenIdConfigurationFails(): Unit = runBlocking {
        var counter = 0
        val engine = MockEngine {
            respond(
                content = when {
                    counter++ == 0 -> "not json"
                    else -> "{}"
                },
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) { expectSuccess = false }
        assertFails {
            client.fetchOpenIdConfiguration("https://issuer.example")
        }
        assertFails {
            client.fetchOpenIdConfiguration("https://issuer.example")
        }
    }

    @Test
    fun testJwkDiscoveryRotation() = testApplication {
        val issuer = "https://issuer.example/"
        val audience = "audience"
        val kid1 = "kid-1"
        val kid2 = "kid-2"

        val keyPair1 = rsaKeyPair()
        val keyPair2 = rsaKeyPair()
        val jwk1 = jwkFor(keyPair1, kid1)
        val jwk2 = jwkFor(keyPair2, kid2)
        val jwkProvider = RotatingJwkProvider(mapOf(kid1 to jwk1))

        install(Authentication) {
            jwt {
                jwk {
                    this.audience = audience
                    jwkProviderFactory { jwkProvider }
                    validate { JWTPrincipal(it.payload) }
                    openIdConfig = OpenIdConfiguration(
                        issuer = issuer,
                        jwksUri = "$issuer/.well-known/openid-configuration"
                    )
                }
            }
        }

        routing {
            authenticate {
                get("/") { call.respondText("ok") }
            }
        }

        val token1 = tokenFor(issuer, audience, kid1, keyPair1.algorithm)
        val response1 = client.get("/") {
            headers.append(HttpHeaders.Authorization, "Bearer $token1")
        }
        assertEquals(HttpStatusCode.OK, response1.status)

        jwkProvider.rotate(mapOf(kid2 to jwk2))

        val token2 = tokenFor(issuer, audience, kid2, keyPair2.algorithm)
        val response2 = client.get("/") {
            headers.append(HttpHeaders.Authorization, "Bearer $token2")
        }
        assertEquals(HttpStatusCode.OK, response2.status)
    }

    private fun withServer(configure: Routing.() -> Unit, block: suspend (Int) -> Unit) = runTest {
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, port) {
            routing(configure)
        }.start(wait = false)

        try {
            block(port)
        } finally {
            server.stopSuspend(gracePeriodMillis = 10, timeoutMillis = 10)
        }
    }

    @Test
    fun testJwkDiscoveryWithValidation() {
        val keyId = "test-key-1"
        val keyPair = rsaKeyPair()
        val jwksJson = jwksJsonFor(keyPair, keyId)

        withServer(configure = {
            get("/jwks") {
                call.respondText(
                    text = jwksJson,
                    contentType = ContentType.Application.Json
                )
            }
        }) { port ->
            testApplication {
                val issuerUrl = "http://127.0.0.1:$port"
                val audience = "test-audience"

                val openIdConfig = OpenIdConfiguration(
                    issuer = issuerUrl,
                    jwksUri = "$issuerUrl/jwks"
                )

                install(Authentication) {
                    jwt("jwt-auth") {
                        jwk {
                            this.audience = audience
                            this.openIdConfig = openIdConfig
                            validate { credential ->
                                when (credential.subject) {
                                    "valid-user" -> JWTPrincipal(credential.payload)
                                    else -> null
                                }
                            }
                        }
                    }
                }

                routing {
                    authenticate("jwt-auth") {
                        get("/protected") {
                            val principal = call.principal<JWTPrincipal>()
                            call.respondText("Hello ${principal?.subject}")
                        }
                    }
                }

                val validToken = tokenFor(
                    keyId = keyId,
                    issuer = issuerUrl,
                    audience = audience,
                    subject = "valid-user",
                    algorithm = keyPair.algorithm
                )

                val validResponse = client.get("/protected") {
                    headers.append(HttpHeaders.Authorization, "Bearer $validToken")
                }
                assertEquals(HttpStatusCode.OK, validResponse.status)

                val invalidToken = tokenFor(
                    keyId = keyId,
                    issuer = issuerUrl,
                    audience = audience,
                    subject = "invalid-user",
                    algorithm = keyPair.algorithm
                )

                val invalidResponse = client.get("/protected") {
                    headers.append(HttpHeaders.Authorization, "Bearer $invalidToken")
                }
                assertEquals(HttpStatusCode.Unauthorized, invalidResponse.status)
            }
        }
    }

    @Test
    fun testJwkProviderFetchesAndCaches() {
        val keyPair = rsaKeyPair()
        val keyId = "kid-1"
        val jwksJson = jwksJsonFor(keyPair, keyId)
        val fetchCount = AtomicInteger(0)

        withServer(configure = {
            get {
                fetchCount.incrementAndGet()
                call.respondText(
                    text = jwksJson,
                    status = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json
                )
            }
        }) { port ->
            val jwkProvider = JwkConfig().apply {
                openIdConfig = OpenIdConfiguration(
                    issuer = "http://issuer.example",
                    jwksUri = "http://127.0.0.1:$port"
                )
                cache(maxEntries = 1, duration = 1.seconds)
            }.toJwkProvider()

            val jwk = jwkProvider.get(keyId)
            assertEquals(1, fetchCount.get())
            assertEquals(jwkFor(keyPair, keyId).id, jwk.id)

            jwkProvider.get(keyId)
            assertEquals(1, fetchCount.get())

            assertFailsWith<SigningKeyNotFoundException> {
                jwkProvider.get("Unknown key")
            }
            assertEquals(2, fetchCount.get())

            assertFailsWith<SigningKeyNotFoundException> {
                jwkProvider.get("Unknown key")
            }
            assertEquals(3, fetchCount.get())
        }
    }

    private class RotatingJwkProvider(initialKeys: Map<String, Jwk>) : JwkProvider {
        private val keys = AtomicReference(initialKeys)

        fun rotate(newKeys: Map<String, Jwk>) {
            keys.set(newKeys)
        }

        override fun get(keyId: String?): Jwk {
            val id = keyId ?: throw SigningKeyNotFoundException("Key id is missing", null)
            return keys.get()[id] ?: throw SigningKeyNotFoundException("Key not found", null)
        }
    }

    private fun rsaKeyPair(): KeyPair {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }
        return keyPair.generateKeyPair()
    }

    private fun jwkFor(keyPair: KeyPair, keyId: String): Jwk {
        val publicKey = keyPair.public as RSAPublicKey
        val values = mapOf(
            "kty" to "RSA",
            "kid" to keyId,
            "alg" to "RS256",
            "use" to "sig",
            "n" to base64Url(publicKey.modulus.toByteArray().stripLeadingZero()),
            "e" to base64Url(publicKey.publicExponent.toByteArray().stripLeadingZero())
        )
        return Jwk.fromValues(values)
    }

    private fun jwksJsonFor(keyPair: KeyPair, keyId: String): String {
        val publicKey = keyPair.public as RSAPublicKey
        val jwksKey = JwksKey(
            keyId = keyId,
            keyType = "RSA",
            algorithm = "RS256",
            usage = "sig",
            modulus = base64Url(publicKey.modulus.toByteArray().stripLeadingZero()),
            exponent = base64Url(publicKey.publicExponent.toByteArray().stripLeadingZero())
        )
        return discoveryJson.encodeToString(JwksKeys(listOf(jwksKey)))
    }

    private val KeyPair.algorithm: Algorithm
        get() {
            val publicKey = public as RSAPublicKey
            val privateKey = private as RSAPrivateKey
            return Algorithm.RSA256(publicKey, privateKey)
        }

    private fun tokenFor(
        issuer: String,
        audience: String,
        keyId: String,
        algorithm: Algorithm,
        subject: String? = null
    ): String {
        val jwt = JWT.create().withIssuer(issuer).withAudience(audience).withKeyId(keyId)
        if (subject != null) jwt.withSubject(subject)
        return jwt.sign(algorithm)
    }

    private fun base64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun ByteArray.stripLeadingZero(): ByteArray =
        if (size > 1 && this[0] == 0.toByte()) copyOfRange(1, size) else this
}
