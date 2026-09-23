/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.*
import io.ktor.client.engine.darwin.certificates.CertificatePinner
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.network.tls.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CertificatePinnerTest {

    @Test
    fun testCertificatePinnerSelfSignedWithValidateTrustDisabled() = runBlocking {
        val pin = discoverSelfSignedCertificatePin()

        val client = HttpClient(Darwin) {
            engine {
                certificatePinner("127.0.0.1", pin, validateTrust = false)
            }
        }

        try {
            val response = client.get(TEST_SERVER_TLS)
            assertEquals("Hello, TLS!", response.bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun testCertificatePinnerFailsWhenPinDoesNotMatch() = runBlocking {
        val client = HttpClient(Darwin) {
            engine {
                certificatePinner(
                    "127.0.0.1",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    validateTrust = false,
                )
            }
        }

        try {
            val failure = assertFailsWith<TlsPeerUnverifiedException> {
                client.get(TEST_SERVER_TLS)
            }
            assertTrue(
                failure.message!!.contains("Certificate pinning failure!"),
                "Expected pinning failure message, got: ${failure.message}",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun testCertificatePinnerFailsWhenValidateTrustEnabledForSelfSigned() = runBlocking {
        val pin = discoverSelfSignedCertificatePin()

        val client = HttpClient(Darwin) {
            engine {
                certificatePinner("127.0.0.1", pin, validateTrust = true)
            }
        }

        try {
            val failure = assertFailsWith<TlsPeerUnverifiedException> {
                client.get(TEST_SERVER_TLS)
            }
            assertEquals("Server trust is invalid", failure.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun testCertificatePinnerDoesNotPinUnmatchedHost() = runBlocking {
        val client = HttpClient(Darwin) {
            engine {
                certificatePinner(
                    "unpinned-host.example",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    validateTrust = false,
                )
            }
        }

        try {
            val failure = assertFails { client.get(TEST_SERVER_TLS) }
            val pinningFailure = failure as? TlsPeerUnverifiedException
            if (pinningFailure != null) {
                assertTrue(
                    !pinningFailure.message!!.contains("Certificate pinning failure!"),
                    "Pinner should not reject certificate when host is not pinned",
                )
            }
        } finally {
            client.close()
        }
    }

    private suspend fun discoverSelfSignedCertificatePin(): String {
        val discoveryClient = HttpClient(Darwin) {
            engine {
                certificatePinner(
                    "127.0.0.1",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    validateTrust = false,
                )
            }
        }

        val pinningFailure = assertFailsWith<TlsPeerUnverifiedException> {
            discoveryClient.get(TEST_SERVER_TLS)
        }
        discoveryClient.close()

        return PIN_PATTERN.find(pinningFailure.message ?: "")
            ?.value
            ?: error("Expected peer certificate pin in pinning failure message")
    }

    private fun DarwinClientEngineConfig.certificatePinner(
        host: String,
        pin: String,
        validateTrust: Boolean,
    ) {
        handleChallenge(
            CertificatePinner.Builder()
                .add(host, pin)
                .validateTrust(validateTrust)
                .build()
        )
    }

    private companion object {
        val PIN_PATTERN = Regex("""sha256/[A-Za-z0-9+/=]+""")
    }
}
