/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.InvalidPublicKeyException
import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.SignatureVerificationException
import io.ktor.http.auth.*
import io.ktor.server.auth.oidc.utils.*
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import kotlin.test.*

class OidcJcaAlgorithmsTest {

    @Test
    fun `verification rejects malformed signature as token error`() {
        val keys = testEdDsaKeys
        val token = keys.accessToken { subject = "user" }
        val tampered = token.substringBeforeLast('.') + ".!!!"
        val algorithm = JcaJwtAlgorithm(SignatureAlgorithm.EDDSA, keys.jwk.toPublicKey(), null)

        assertFailsWith<SignatureVerificationException> { algorithm.verify(JWT.decode(tampered)) }
    }

    @Test
    fun `verification rejects key of the wrong type as token error`() {
        val token = testEdDsaKeys.accessToken { subject = "user" }
        val rsaKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        val algorithm = JcaJwtAlgorithm(SignatureAlgorithm.EDDSA, rsaKey, null)

        assertFailsWith<SignatureVerificationException> { algorithm.verify(JWT.decode(token)) }
    }

    @Test
    fun `verification propagates JVM configuration errors`() {
        val keys = testEdDsaKeys
        val token = keys.accessToken { subject = "user" }
        val unavailable = SignatureAlgorithm(
            name = "Unavailable",
            jcaAlgorithm = "NoSuchSignatureAlgorithm",
            digestAlgorithm = DigestAlgorithm.SHA_512,
            keyAlgorithm = KeyAlgorithm.OKP,
            jwaName = "EdDSA",
        )
        val algorithm = JcaJwtAlgorithm(unavailable, keys.jwk.toPublicKey(), null)

        assertFailsWith<NoSuchAlgorithmException> { algorithm.verify(JWT.decode(token)) }
    }

    @Test
    fun `OKP JWK accepts padded x parameter`() {
        val source = testEdDsaKeys.jwk
        val x = source.additionalAttributes["x"] as String
        val padded = Jwk.fromValues(
            mapOf("kid" to source.id, "kty" to "OKP", "crv" to "Ed25519", "x" to "$x=")
        )
        assertEquals(source.toPublicKey(), padded.toPublicKey())
    }

    @Test
    fun `OKP JWK rejects invalid x parameter`() {
        val jwk = Jwk.fromValues(mapOf("kid" to "kid", "kty" to "OKP", "crv" to "Ed25519", "x" to "!!!"))
        assertFailsWith<InvalidPublicKeyException> { jwk.toPublicKey() }
    }
}
