/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.junit.*
import java.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.test.*
import kotlin.test.Test

class CIOHttpsTest : TestWithKtor() {

    override val server: ApplicationEngine = embeddedServer(Jetty, applicationEngineEnvironment {
        sslConnector(keyStore, "sha384ecdsa", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
            port = serverPort
            keyStorePath = keyStoreFile.absoluteFile

            module {
                routing {
                    get("/") {
                        call.respondText("Hello, world")
                    }
                }
            }
        }
    })

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        private lateinit var sslContext: SSLContext
        lateinit var x509TrustManager: X509TrustManager

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = buildKeyStore {
                certificate("sha384ecdsa") {
                    hash = HashAlgorithm.SHA384
                    sign = SignatureAlgorithm.ECDSA
                    keySizeInBits = 384
                    password = "changeit"
                }
                certificate("sha256ecdsa") {
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.ECDSA
                    keySizeInBits = 256
                    password = "changeit"
                }
                certificate("sha384rsa") {
                    hash = HashAlgorithm.SHA384
                    sign = SignatureAlgorithm.RSA
                    keySizeInBits = 1024
                    password = "changeit"
                }
                certificate("sha1rsa") {
                    hash = HashAlgorithm.SHA1
                    sign = SignatureAlgorithm.RSA
                    keySizeInBits = 1024
                    password = "changeit"
                }
            }

            keyStore.saveToFile(keyStoreFile, "changeit")
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            x509TrustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

    }

    @Test
    fun hello(): Unit {
        CIOCipherSuites.SupportedSuites.forEach { suite ->
            /**
             * Outdated by jetty.
             */
            if (suite == CIOCipherSuites.ECDHE_ECDSA_AES128_SHA256) return@forEach

            /**
             * Too strong for old JDK.
             */
            if (suite == CIOCipherSuites.ECDHE_ECDSA_AES256_SHA384) return@forEach

            /**
             * Deprecated since jdk11.
             */
            if (suite == CIOCipherSuites.ECDHE_RSA_AES128_SHA256) return@forEach
            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES_128_GCM_SHA256) return@forEach

//            Mandatory
//            if (suite == CIOCipherSuites.TLS_RSA_WITH_AES128_CBC_SHA) return@forEach

            clientTest(CIO) {
                config {
                    engine {
                        https {
                            trustManager = x509TrustManager
                            cipherSuites = listOf(suite)
                        }
                    }
                }

                test { client ->
                    try {
                        println("Starting: ${suite.name}")
                        val actual = client.get<String>("https://127.0.0.1:$serverPort/")
                        assertEquals("Hello, world", actual)
                    } catch (cause: Throwable) {
                        println("${suite.name}: $cause")
                        client.cancel()
                        fail("${suite.name}: $cause")
                    }
                }
            }
        }
    }

    @Test
    fun external(): Unit = clientTest(CIO) {
        test { client ->
            client.get<HttpStatement>("https://kotlinlang.org").execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }

    @Test
    fun customDomainsTest(): Unit = clientTest(CIO) {
        val domains = listOf(
            "https://google.com",
            "https://facebook.com",
            "https://elster.de",
            "https://freenode.net"
        )

        config {
            expectSuccess = false
        }

        test { client ->
            domains.forEach { url ->
                client.get<String>(url)
            }
        }
    }

    @Test
    fun repeatRequestTest(): Unit = clientTest(CIO) {
        config {
            followRedirects = false

            engine {
                maxConnectionsCount = 1_000_000
                pipelining = true
                endpoint.apply {
                    connectRetryAttempts = 1
                    maxConnectionsPerRoute = 10_000
                }
            }
        }

        test { client ->
            val testSize = 10
            var received = 0
            client.async {
                repeat(testSize) {
                    client.get<HttpStatement>("https://www.facebook.com").execute { response ->
                        assertTrue(response.status.isSuccess())
                        received++
                    }
                }
            }.await()

            assertEquals(testSize, received)
        }
    }
}
