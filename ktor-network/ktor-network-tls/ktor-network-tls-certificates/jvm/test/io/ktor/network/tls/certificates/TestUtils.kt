/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.*
import java.io.*
import java.security.*
import java.security.cert.*
import java.security.interfaces.*
import java.time.*
import java.util.*
import kotlin.test.*

internal fun assertHasPrivateKey(keyStore: KeyStore, alias: String, password: String, algorithm: String, size: Int) {
    val key = keyStore.getKey(alias, password.toCharArray())
    assertNotNull(key, "A key with alias '$alias' should be generated")
    assertIs<PrivateKey>(key)
    assertEquals(algorithm, key.algorithm)

    val actualKeySize = when (key) {
        is RSAKey -> key.modulus.bitLength()
        is ECKey -> key.params.order.bitLength()
        else -> error("cannot get key size")
    }
    assertEquals(size, actualKeySize)
}

internal fun assertHasX509Certificate(keyStore: KeyStore, alias: String, algorithm: String): X509Certificate {
    val cert = keyStore.getCertificate(alias)
    assertNotNull(cert, "A certificate with alias '$alias' should be generated")
    assertIs<X509Certificate>(cert)
    assertEquals("X.509", cert.type)
    assertEquals(algorithm, cert.sigAlgName)
    return cert
}

internal fun assertValidityRange(cert: X509Certificate, from: Instant, until: Instant) {
    assertEquals(from, cert.notBefore.toInstant())
    assertEquals(until, cert.notAfter.toInstant())

    assertNotYetValidAt(cert, from.minusSeconds(1))
    assertValidAt(cert, from)
    assertValidAt(cert, until)
    assertExpiredAt(cert, until.plusSeconds(1))
}

private fun assertNotYetValidAt(cert: X509Certificate, instant: Instant) {
    assertFailsWith<CertificateNotYetValidException>("the generated certificate should not be valid yet at $instant") {
        cert.checkValidity(Date.from(instant))
    }
}

private fun assertValidAt(cert: X509Certificate, instant: Instant) {
    try {
        cert.checkValidity(Date.from(instant))
    } catch (e: CertificateNotYetValidException) {
        fail("the generated certificate should already be valid at $instant, but is not yet: $e")
    } catch (e: CertificateExpiredException) {
        fail("the generated certificate should still be valid at $instant, but is already expired: $e")
    }
}

private fun assertExpiredAt(cert: X509Certificate, instant: Instant) {
    assertFailsWith<CertificateExpiredException>("the generated certificate should be expired by $instant") {
        cert.checkValidity(Date.from(instant))
    }
}

internal fun assertExtensionsForServerKeyType(
    cert: X509Certificate,
    expectedDomains: List<String>,
    expectedIPs: List<String>,
) {
    assertEquals(-1, cert.basicConstraints, "there should be no basic constraints for Server key type")
    assertEquals(
        expected = listOf(OID.ServerAuth.identifier),
        actual = cert.extendedKeyUsage,
        message = "server auth should be added for Server key type"
    )
    val expectedAlternativeNames = expectedDomains.map { listOf(2, it) } + expectedIPs.map { listOf(7, it) }
    assertContentEquals(expectedAlternativeNames, cert.subjectAlternativeNames)
}

internal fun assertExtensionsForClientKeyType(cert: X509Certificate) {
    assertEquals(-1, cert.basicConstraints, "there should be no basic constraints for Client key type")
    assertEquals(
        expected = listOf(OID.ClientAuth.identifier),
        actual = cert.extendedKeyUsage,
        message = "client auth should be added for Client key type"
    )
    assertNull(cert.subjectAlternativeNames, "there should be no subject alternative names for Client key type")
}

internal fun assertExtensionsForCAKeyType(cert: X509Certificate) {
    assertEquals(
        Int.MAX_VALUE,
        cert.basicConstraints,
        "basic constraints for CA key type should be Int.MAX_VALUE for 'no limit'"
    )
    assertNull(cert.extendedKeyUsage, "there should be no extended key usage info for CA key type")
    assertNull(cert.subjectAlternativeNames, "there should be no subject alternative names for CA key type")
}

internal fun loadJksFromFile(file: File, password: String): KeyStore = KeyStore.getInstance("JKS").apply {
    file.inputStream().use {
        load(it, password.toCharArray())
    }
}
