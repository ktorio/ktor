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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CertificatePinnerTest : ClientEngineTest<DarwinClientEngineConfig>(Darwin) {

    @Test
    fun `pinning accepts self-signed certificate without trust validation`() = testClient {
        val pin = discoverSelfSignedCertificatePin()

        config {
            engine {
                certificatePinner("127.0.0.1", pin, validateTrust = false)
            }
        }

        test { client ->
            val response = client.get(TEST_SERVER_TLS)
            assertEquals("Hello, TLS!", response.bodyAsText())
        }
    }

    @Test
    fun `pinning rejects certificate when hash does not match`() = testClient {
        config {
            engine {
                certificatePinner(
                    "127.0.0.1",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    validateTrust = false,
                )
            }
        }

        test { client ->
            val failure = assertFailsWith<TlsPeerUnverifiedException> {
                client.get(TEST_SERVER_TLS)
            }
            assertTrue(
                failure.message!!.contains("Certificate pinning failure!"),
                "Expected pinning failure message, got: ${failure.message}",
            )
        }
    }

    @Test
    fun `pinning rejects self-signed certificate when trust validation is enabled`() = testClient {
        val pin = discoverSelfSignedCertificatePin()

        config {
            engine {
                certificatePinner("127.0.0.1", pin, validateTrust = true)
            }
        }

        test { client ->
            val failure = assertFailsWith<TlsPeerUnverifiedException> {
                client.get(TEST_SERVER_TLS)
            }
            assertEquals("Server trust is invalid", failure.message)
        }
    }

    @Test
    fun `pinner does not enforce pins for other hosts`() = testClient {
        config {
            engine {
                certificatePinner(
                    "unpinned-host.example",
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    validateTrust = false,
                )
            }
        }

        test { client ->
            val failure = assertFailsWith<Throwable> {
                client.get(TEST_SERVER_TLS)
            }
            if (failure is TlsPeerUnverifiedException) {
                assertTrue(
                    !failure.message!!.contains("Certificate pinning failure!"),
                    "Pinner should not reject certificate when host is not pinned",
                )
            }
        }
    }

    private fun discoverSelfSignedCertificatePin(): String {
        var pin: String? = null

        testClient {
            config {
                engine {
                    certificatePinner(
                        "127.0.0.1",
                        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        validateTrust = false,
                    )
                }
            }

            test { client ->
                val pinningFailure = assertFailsWith<TlsPeerUnverifiedException> {
                    client.get(TEST_SERVER_TLS)
                }
                pin = PIN_PATTERN.find(pinningFailure.message ?: "")
                    ?.value
                    ?: error("Expected peer certificate pin in pinning failure message")
            }
        }

        return pin ?: error("Expected peer certificate pin in pinning failure message")
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
