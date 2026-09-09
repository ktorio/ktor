/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.auth.*
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
}
