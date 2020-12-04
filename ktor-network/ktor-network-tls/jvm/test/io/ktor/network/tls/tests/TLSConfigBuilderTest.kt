/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.tests

import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import kotlin.test.*

internal class TLSConfigBuilderTest {
    private val keyStore = buildKeyStore {
        certificate("first") {
            hash = HashAlgorithm.SHA256
            sign = SignatureAlgorithm.RSA
            password = ""
        }
        certificate("second") {
            hash = HashAlgorithm.SHA256
            sign = SignatureAlgorithm.RSA
            password = ""
        }
    }

    @Test
    fun useAllCertificates() {
        val config = TLSConfigBuilder().apply {
            addKeyStore(keyStore, "".toCharArray())
        }
        assertEquals(2, config.certificates.size)
    }

    @Test
    fun specificAliasInKeyStore() {
        val config = TLSConfigBuilder().apply {
            addKeyStore(keyStore, "".toCharArray(), "first")
        }
        assertEquals(1, config.certificates.size)
    }
}
