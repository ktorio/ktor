/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.openapi.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.testing.*
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JvmSecuritySchemeInferenceTest {

    private val jwtSecret = "test-jwt-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    @Test
    fun testInferDigestAuthenticationProvider() = testApplication {
        install(Authentication) {
            digest("digest-auth") {
                realm = "Digest Realm"
                digestProvider { userName, realm ->
                    if (userName == "user") {
                        MessageDigest.getInstance("MD5").digest("$userName:$realm:password".toByteArray())
                    } else {
                        null
                    }
                }
            }

            digest("described-digest", "Digest Authentication for Legacy Systems") {
                realm = "Legacy"
                digestProvider { userName, realm ->
                    MessageDigest.getInstance("MD5").digest("$userName:$realm:pass".toByteArray())
                }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme1 = schemes["digest-auth"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme1.type)
        assertEquals("digest", scheme1.scheme)
        assertEquals("HTTP Digest Authentication", scheme1.description)
        assertNull(scheme1.bearerFormat)

        val scheme2 = schemes["described-digest"] as HttpSecurityScheme
        assertEquals("Digest Authentication for Legacy Systems", scheme2.description)
    }

    @Test
    fun testInferJWTAuthenticationProvider() = testApplication {
        install(Authentication) {
            jwt("jwt-auth") {
                realm = "JWT Realm"
                verifier(JWT.require(jwtAlgorithm).withAudience(jwtAudience).withIssuer(jwtIssuer).build())
                validate { credential ->
                    if (credential.payload.audience.contains(jwtAudience)) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme = schemes["jwt-auth"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)
        assertEquals("JWT Bearer Authentication", scheme.description)
    }

    @Test
    fun testInferJWTAuthWithCustomAuthSchemes() = testApplication {
        install(Authentication) {
            jwt("jwt-custom-schemes") {
                authSchemes("Bearer", "Token")
                verifier(JWT.require(jwtAlgorithm).withIssuer(jwtIssuer).build())
                validate { JWTPrincipal(it.payload) }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        val scheme = schemes?.get("jwt-custom-schemes") as HttpSecurityScheme

        // Inference always produces a standard-bearer scheme
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)
    }

    @Test
    fun testRegisterDigestAuthSecurityScheme() = testApplication {
        application.registerDigestAuthSecurityScheme("custom-digest")
        application.registerDigestAuthSecurityScheme("digest-custom", "Custom Digest Auth")
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme1 = schemes["custom-digest"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme1.type)
        assertEquals("digest", scheme1.scheme)
        assertEquals("HTTP Digest Authentication", scheme1.description)
        assertNull(scheme1.bearerFormat)

        val scheme2 = schemes["digest-custom"] as HttpSecurityScheme

        assertEquals("digest", scheme2.scheme)
        assertEquals("Custom Digest Auth", scheme2.description)
    }

    @Test
    fun testRegisterJWTSecurityScheme() = testApplication {
        application.registerJWTSecurityScheme("custom-jwt")
        application.registerJWTSecurityScheme("jwt-custom", "Custom JWT Auth")
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme1 = schemes["custom-jwt"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme1.type)
        assertEquals("bearer", scheme1.scheme)
        assertEquals("JWT", scheme1.bearerFormat)
        assertEquals("JWT Bearer Authentication", scheme1.description)

        val scheme2 = schemes["jwt-custom"] as HttpSecurityScheme
        assertEquals("Custom JWT Auth", scheme2.description)
    }
}
