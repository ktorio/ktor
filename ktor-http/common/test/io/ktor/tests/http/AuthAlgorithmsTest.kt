/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.auth.*
import kotlin.test.*

class AuthAlgorithmsTest {
    @Test
    fun `digest algorithms expose XML digest URIs`() {
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256", DigestAlgorithm.SHA_256.uri)
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#sha384", DigestAlgorithm.SHA_384.uri)
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha512", DigestAlgorithm.SHA_512.uri)
        assertNull(DigestAlgorithm.SHA_256_SESS.uri)
        assertNull(DigestAlgorithm("custom", "SHA-256", isSession = false).uri)
    }

    @Test
    fun `digest algorithms can be resolved by XML digest URI`() {
        assertEquals(DigestAlgorithm.SHA_256, DigestAlgorithm.fromUri("http://www.w3.org/2001/04/xmlenc#sha256"))
        assertEquals(DigestAlgorithm.SHA_384, DigestAlgorithm.fromUri("http://www.w3.org/2001/04/xmldsig-more#sha384"))
        assertEquals(DigestAlgorithm.SHA_512, DigestAlgorithm.fromUri("http://www.w3.org/2001/04/xmlenc#sha512"))
        assertNull(DigestAlgorithm.fromUri("http://www.example.com/unknown"))
    }

    @Test
    fun `digest auth name lookup remains HTTP Digest specific`() {
        assertEquals(DigestAlgorithm.SHA_256, DigestAlgorithm.from("sha-256"))
        assertEquals(DigestAlgorithm.SHA_512_256, DigestAlgorithm.from("SHA-512-256"))
        assertNull(DigestAlgorithm.from("SHA-384"))
        assertNull(DigestAlgorithm.from("SHA-512"))
        assertNull(DigestAlgorithm.from("http://www.w3.org/2001/04/xmlenc#sha256"))
    }

    @Test
    fun `key algorithms expose common families`() {
        assertEquals("RSA", KeyAlgorithm.RSA.name)
        assertEquals("EC", KeyAlgorithm.EC.name)
        assertEquals("HMAC", KeyAlgorithm.HMAC.name)
        assertEquals("OKP", KeyAlgorithm.OKP.name)
        assertEquals(KeyAlgorithm("DSA"), KeyAlgorithm("DSA"))
    }

    @Test
    fun `signature algorithms expose common metadata`() {
        assertEquals("RSA-SHA-256", SignatureAlgorithm.RSA_SHA_256.name)
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", SignatureAlgorithm.RSA_SHA_256.xmlUri)
        assertEquals("RS256", SignatureAlgorithm.RSA_SHA_256.jwaName)
        assertEquals("SHA256withRSA", SignatureAlgorithm.RSA_SHA_256.jcaAlgorithm)
        assertEquals(DigestAlgorithm.SHA_256, SignatureAlgorithm.RSA_SHA_256.digestAlgorithm)
        assertEquals(KeyAlgorithm.RSA, SignatureAlgorithm.RSA_SHA_256.keyAlgorithm)

        assertEquals("ECDSA-SHA-512", SignatureAlgorithm.ECDSA_SHA_512.name)
        assertEquals("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512", SignatureAlgorithm.ECDSA_SHA_512.xmlUri)
        assertEquals("ES512", SignatureAlgorithm.ECDSA_SHA_512.jwaName)
        assertEquals("SHA512withECDSA", SignatureAlgorithm.ECDSA_SHA_512.jcaAlgorithm)
        assertEquals(DigestAlgorithm.SHA_512, SignatureAlgorithm.ECDSA_SHA_512.digestAlgorithm)
        assertEquals(KeyAlgorithm.EC, SignatureAlgorithm.ECDSA_SHA_512.keyAlgorithm)
    }

    @Test
    fun `signature algorithms can be resolved by XML Signature URI`() {
        assertEquals(
            SignatureAlgorithm.RSA_SHA_384,
            SignatureAlgorithm.fromXmlUri("http://www.w3.org/2001/04/xmldsig-more#rsa-sha384")
        )
        assertEquals(
            SignatureAlgorithm.ECDSA_SHA_256,
            SignatureAlgorithm.fromXmlUri("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256")
        )
        assertNull(SignatureAlgorithm.fromXmlUri("http://www.example.com/unknown"))
    }

    @Test
    fun `signature algorithms can be resolved by JWA name`() {
        assertEquals(SignatureAlgorithm.RSA_SHA_256, SignatureAlgorithm.fromJwaName("RS256"))
        assertEquals(SignatureAlgorithm.ECDSA_SHA_384, SignatureAlgorithm.fromJwaName("ES384"))
        assertNull(SignatureAlgorithm.fromJwaName("HS256"))
        assertNull(SignatureAlgorithm.fromJwaName("none"))
    }

    @Test
    fun `signature algorithm equality is based on name`() {
        val sameName = SignatureAlgorithm(
            name = SignatureAlgorithm.RSA_SHA_256.name,
            jcaAlgorithm = "different",
            digestAlgorithm = DigestAlgorithm.SHA_512,
            keyAlgorithm = KeyAlgorithm.EC,
            xmlUri = "http://www.example.com/different",
            jwaName = "DIFFERENT"
        )
        val sameXmlUri = SignatureAlgorithm(
            name = "Different",
            jcaAlgorithm = SignatureAlgorithm.RSA_SHA_256.jcaAlgorithm,
            digestAlgorithm = SignatureAlgorithm.RSA_SHA_256.digestAlgorithm,
            keyAlgorithm = SignatureAlgorithm.RSA_SHA_256.keyAlgorithm,
            xmlUri = SignatureAlgorithm.RSA_SHA_256.xmlUri,
            jwaName = SignatureAlgorithm.RSA_SHA_256.jwaName
        )

        assertEquals(SignatureAlgorithm.RSA_SHA_256, sameName)
        assertEquals(SignatureAlgorithm.RSA_SHA_256.hashCode(), sameName.hashCode())
        assertNotEquals(SignatureAlgorithm.RSA_SHA_256, sameXmlUri)
        assertNotEquals(SignatureAlgorithm.RSA_SHA_256, SignatureAlgorithm.ECDSA_SHA_256)
    }
}
