/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.auth.*
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PSSParameterSpec
import kotlin.test.*

class DigestAlgorithmJvmTest {
    @Test
    fun `toDigester supports XML digest algorithms`() {
        assertEquals(32, DigestAlgorithm.SHA_256.toDigester().digest("ktor".toByteArray()).size)
        assertEquals(48, DigestAlgorithm.SHA_384.toDigester().digest("ktor".toByteArray()).size)
        assertEquals(64, DigestAlgorithm.SHA_512.toDigester().digest("ktor".toByteArray()).size)
    }

    @Test
    fun `toJcaSignature supports signature algorithms`() {
        assertEquals("SHA256withRSA", SignatureAlgorithm.RSA_SHA_256.toJcaSignature().algorithm)
        assertEquals("SHA512withECDSA", SignatureAlgorithm.ECDSA_SHA_512.toJcaSignature().algorithm)
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_11, disabledReason = "RSASSA-PSS is available since JDK 11")
    fun `toJcaSignature configures RSASSA-PSS parameters`() {
        val algorithms = mapOf(
            SignatureAlgorithm.RSA_PSS_SHA_256 to ("SHA-256" to 32),
            SignatureAlgorithm.RSA_PSS_SHA_384 to ("SHA-384" to 48),
            SignatureAlgorithm.RSA_PSS_SHA_512 to ("SHA-512" to 64),
        )
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        for ((algorithm, expected) in algorithms) {
            val parameters = algorithm.toJcaSignature().parameters.getParameterSpec(PSSParameterSpec::class.java)
            assertEquals(expected.first, parameters.digestAlgorithm)
            assertEquals(expected.second, parameters.saltLength)
            assertTrue(algorithm.signAndVerify(keyPair), "${algorithm.name} round trip failed")
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_15, disabledReason = "Ed25519 is available since JDK 15")
    fun `toJcaSignature supports Ed25519`() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        assertTrue(SignatureAlgorithm.EDDSA.signAndVerify(keyPair))
        assertTrue(SignatureAlgorithm.ED25519.signAndVerify(keyPair))
    }

    private fun SignatureAlgorithm.signAndVerify(keyPair: KeyPair): Boolean {
        val data = "ktor".toByteArray()
        val signature = toJcaSignature().run {
            initSign(keyPair.private)
            update(data)
            sign()
        }
        return toJcaSignature().run {
            initVerify(keyPair.public)
            update(data)
            verify(signature)
        }
    }
}
