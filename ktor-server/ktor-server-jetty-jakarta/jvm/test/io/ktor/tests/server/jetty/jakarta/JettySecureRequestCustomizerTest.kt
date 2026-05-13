/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import org.eclipse.jetty.server.*
import java.io.*
import kotlin.test.*

class JettySecureRequestCustomizerTest {

    @Test
    fun `secureRequestCustomizer block is applied to HTTPS connectors`() {
        val keyStorePassword = "changeit"
        val keyAlias = "sampleAlias"
        val keyStore = buildKeyStore {
            certificate(keyAlias) {
                password = keyStorePassword
                domains = listOf("localhost")
            }
        }
        val keyStoreFile = File.createTempFile("ktor-7458-keystore", ".jks").apply { deleteOnExit() }
        keyStore.saveToFile(keyStoreFile, keyStorePassword)

        var capturedCustomizer: SecureRequestCustomizer? = null
        var invocations = 0

        val server = embeddedServer(
            Jetty,
            configure = {
                secureRequestCustomizer = {
                    capturedCustomizer = this
                    invocations++
                    isSniHostCheck = false
                    isSniRequired = true
                }
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = { keyStorePassword.toCharArray() },
                    privateKeyPassword = { keyStorePassword.toCharArray() },
                ) {
                    port = 0
                    keyStorePath = keyStoreFile
                }
            },
        ) {}
        try {
            server.start(wait = false)
            val customizer = assertNotNull(capturedCustomizer, "secureRequestCustomizer block was not invoked")
            assertEquals(
                1,
                invocations,
                "secureRequestCustomizer block must be invoked exactly once per HTTPS connector"
            )
            assertFalse(customizer.isSniHostCheck, "isSniHostCheck override must be applied")
            assertTrue(customizer.isSniRequired, "isSniRequired override must be applied")
        } finally {
            server.stop(50, 1000)
        }
    }

    @Test
    fun `secureRequestCustomizer block is not invoked when no HTTPS connector is configured`() {
        var invoked = false

        val server = embeddedServer(
            Jetty,
            configure = {
                secureRequestCustomizer = { invoked = true }
                connector { port = 0 }
            },
        ) {}
        try {
            server.start(wait = false)
            assertFalse(invoked, "secureRequestCustomizer block must not be invoked for plain HTTP connectors")
        } finally {
            server.stop(50, 1000)
        }
    }
}
