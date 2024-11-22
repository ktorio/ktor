/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.extensions.*
import java.io.File
import java.time.temporal.ChronoUnit
import javax.security.auth.x500.X500Principal
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CertificatesTest {

    private val jbLocalhost = X500Principal("CN=localhost, OU=Kotlin, O=JetBrains, C=RU")
    private val jbLocalhostCA = X500Principal("CN=localhostCA, OU=Kotlin, O=JetBrains, C=RU")

    @BeforeTest
    fun fixCurrentTime() {
        fixCurrentTimeTo(nowInTests)
    }

    @Test
    fun generateCertificate_default() {
        val keyStore = generateCertificate()

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertValidityRange(cert, from = nowInTests, until = nowInTests.plus(3, ChronoUnit.DAYS))

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhost, cert.issuerX500Principal)

        val expectedDomains = listOf("127.0.0.1", "localhost")
        val expectedIPs = listOf("127.0.0.1")
        assertExtensionsForServerKeyType(cert, expectedDomains, expectedIPs)
    }

    @Test
    fun generateCertificate_alsoStoresTheCertificateWithCertSuffix() {
        val keyStore = generateCertificate(keyAlias = "theKeyAlias")

        val aliases = keyStore.aliases().asSequence().toSet()
        val expectedAliases = setOf("thekeyalias", "thekeyaliascert") // the keystore lowercases the aliases
        assertEquals(expectedAliases, aliases, "Should store the certificate also with -cert suffix")

        val certWithKeyAlias = assertHasX509Certificate(keyStore, alias = "theKeyAlias", algorithm = "SHA1withRSA")
        val certWithOwnAlias = assertHasX509Certificate(keyStore, alias = "theKeyAliasCert", algorithm = "SHA1withRSA")
        assertEquals(certWithKeyAlias, certWithOwnAlias)
    }

    @Test
    fun generateCertificateWithCA_default() {
        val caKeyStore = generateCertificate(keyAlias = "caKey", keyType = KeyType.CA)
        val keyStore = caKeyStore.generateCertificate(caKeyAlias = "caKey")

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertValidityRange(cert, from = nowInTests, until = nowInTests.plus(3, ChronoUnit.DAYS))

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhostCA, cert.issuerX500Principal)

        val expectedDomains = listOf("127.0.0.1", "localhost")
        val expectedIPs = listOf("127.0.0.1")
        assertExtensionsForServerKeyType(cert, expectedDomains, expectedIPs)
    }

    @Test
    fun generateCertificate_onlyGeneratesOneAlias() {
        val caKeyStore = generateCertificate(keyAlias = "caKey", keyType = KeyType.CA)
        val keyStore = caKeyStore.generateCertificate(caKeyAlias = "caKey", keyAlias = "the-key")

        val aliases = keyStore.aliases().asSequence().toSet()
        assertEquals(setOf("the-key"), aliases)
    }

    @Test
    fun generateCertificate_keyTypeClient() {
        val keyStore = generateCertificate(keyType = KeyType.Client)

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhost, cert.issuerX500Principal)

        assertExtensionsForClientKeyType(cert)
    }

    @Test
    fun generateCertificateWithCA_keyTypeClient() {
        val caKeyStore = generateCertificate(keyAlias = "caKey", keyType = KeyType.CA)
        val keyStore = caKeyStore.generateCertificate(caKeyAlias = "caKey", keyType = KeyType.Client)

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhostCA, cert.issuerX500Principal)

        assertExtensionsForClientKeyType(cert)
    }

    @Test
    fun generateCertificate_keyTypeCA() {
        val keyStore = generateCertificate(keyType = KeyType.CA)

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertEquals(jbLocalhostCA, cert.subjectX500Principal)
        assertEquals(jbLocalhostCA, cert.issuerX500Principal)

        assertExtensionsForCAKeyType(cert)
    }

    @Test
    fun generateCertificateWithCA_keyTypeCA() {
        val caKeyStore = generateCertificate(keyAlias = "caKey", keyType = KeyType.CA)
        val keyStore = caKeyStore.generateCertificate(caKeyAlias = "caKey", keyType = KeyType.CA)

        assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhostCA, cert.issuerX500Principal)

        assertExtensionsForCAKeyType(cert)
    }

    @Test
    fun generateCertificate_customValues() {
        val keyStore = generateCertificate(
            algorithm = HashAndSign(HashAlgorithm.SHA256, SignatureAlgorithm.ECDSA).name,
            keyAlias = "customAlias",
            keyPassword = "customPassword",
            keySizeInBits = 256,
        )

        assertHasPrivateKey(keyStore, alias = "customAlias", password = "customPassword", algorithm = "EC", size = 256)
        assertHasX509Certificate(keyStore, alias = "customAlias", algorithm = "SHA256withECDSA")
    }

    @Test
    fun generateCertificate_writeToFile() {
        val testKeyStoreFile = File.createTempFile("test-keystore", ".jks")
        try {
            generateCertificate(file = testKeyStoreFile)
            val keyStore = loadJksFromFile(testKeyStoreFile, password = "changeit")

            assertHasPrivateKey(keyStore, alias = "mykey", password = "changeit", algorithm = "RSA", size = 1024)
            assertHasX509Certificate(keyStore, "mykey", "SHA1withRSA")
        } finally {
            testKeyStoreFile.delete()
        }
    }

    @Test
    fun generateCertificate_writeToFile_customKeyPassword() {
        val testKeyStoreFile = File.createTempFile("test-keystore", ".jks")
        try {
            generateCertificate(file = testKeyStoreFile, keyPassword = "new-password")
            val keyStore = loadJksFromFile(testKeyStoreFile, password = "new-password")

            assertHasPrivateKey(keyStore, alias = "mykey", password = "new-password", algorithm = "RSA", size = 1024)
            assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")
        } finally {
            testKeyStoreFile.delete()
        }
    }

    @Test
    fun generateCertificate_writeToFile_customKeyAndJksPassword() {
        val testKeyStoreFile = File.createTempFile("test-keystore", ".jks")
        try {
            generateCertificate(file = testKeyStoreFile, keyPassword = "keyPass", jksPassword = "jksPass")
            val keyStore = loadJksFromFile(testKeyStoreFile, password = "jksPass")

            assertHasPrivateKey(keyStore, alias = "mykey", password = "keyPass", algorithm = "RSA", size = 1024)
            assertHasX509Certificate(keyStore, alias = "mykey", algorithm = "SHA1withRSA")
        } finally {
            testKeyStoreFile.delete()
        }
    }
}
