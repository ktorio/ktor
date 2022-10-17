/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.extensions.*
import java.time.temporal.*
import javax.security.auth.x500.*
import kotlin.test.*

class KeyStoreBuilderTest {

    private val jbLocalhost = X500Principal("CN=localhost, OU=Kotlin, O=JetBrains, C=RU")

    @BeforeTest
    fun fixCurrentTime() {
        fixCurrentTimeTo(nowInTests)
    }

    @Test
    fun buildKeyStore_minimal() {
        val keyStore = buildKeyStore {
            certificate(alias = "someKey") {
                hash = HashAlgorithm.SHA1
                sign = SignatureAlgorithm.RSA
                password = "keyPass"
            }
        }
        val aliases = keyStore.aliases().asSequence().toSet()
        assertEquals(setOf("somekey"), aliases) // the keystore lowercases aliases, and is case-insensitive

        assertHasPrivateKey(keyStore, alias = "someKey", password = "keyPass", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "someKey", algorithm = "SHA1withRSA")

        assertValidityRange(cert, from = nowInTests, until = nowInTests.plus(3, ChronoUnit.DAYS))

        assertEquals(jbLocalhost, cert.subjectX500Principal)
        assertEquals(jbLocalhost, cert.issuerX500Principal)

        assertExtensionsForServerKeyType(cert, expectedDomains = listOf("localhost"), expectedIPs = listOf("127.0.0.1"))
    }

    @Test
    fun buildKeyStore_customAlgorithmAndKeySize() {
        val keyStore = buildKeyStore {
            certificate(alias = "someKey") {
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.ECDSA
                password = "keyPass"
                keySizeInBits = 128
            }
        }

        assertHasPrivateKey(keyStore, alias = "someKey", password = "keyPass", algorithm = "EC", size = 128)
        assertHasX509Certificate(keyStore, alias = "someKey", algorithm = "SHA256withECDSA")
    }

    @Test
    fun buildKeyStore_customValidity() {
        val keyStore = buildKeyStore {
            certificate(alias = "someKey") {
                hash = HashAlgorithm.SHA1
                sign = SignatureAlgorithm.RSA
                password = "keyPass"
                daysValid = 5
            }
        }

        assertHasPrivateKey(keyStore, alias = "someKey", password = "keyPass", algorithm = "RSA", size = 1024)
        val cert = assertHasX509Certificate(keyStore, alias = "someKey", algorithm = "SHA1withRSA")

        assertValidityRange(cert, from = nowInTests, until = nowInTests.plus(5, ChronoUnit.DAYS))
    }
}
