/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.tomcat.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.test.*
import io.ktor.server.cio.CIO as CIOServer

class TLSClientAuthenticationTest {

    @Test
    fun `Jetty Server requesting Client Certificate from CIO Client`() {
        `Server requesting Client Certificate from CIO Client`(Jetty)
    }

    @Test
    fun `Netty Server requesting Client Certificate from CIO Client`() {
        `Server requesting Client Certificate from CIO Client`(Netty)
    }

    @Test
    fun `CIO Server requesting Client Certificate from CIO Client`() {
        val httpsNotImplemented = assertFailsWith<UnsupportedOperationException> {
            `Server requesting Client Certificate from CIO Client`(CIOServer)
        }
    }

    @Test
    fun `Tomcat Server requesting Client Certificate from CIO Client`() {
        `Server requesting Client Certificate from CIO Client`(Tomcat)
    }

    private fun <TEngine : ApplicationEngine,
        TConfiguration : ApplicationEngine.Configuration,
        Factory : ApplicationEngineFactory<TEngine, TConfiguration>>
        `Server requesting Client Certificate from CIO Client`(engine: Factory) = runBlocking {
        val ca = generateCertificate(keyType = KeyType.CA)
        val serverKeyPath = File.createTempFile("server", "jks")
        val serverKeys = ca.generateCertificate(serverKeyPath, keyType = KeyType.Server)
        val clientKeys = ca.generateCertificate(keyType = KeyType.Client)

        val caTrustStorePath = File.createTempFile("trustStore", "jks")
        val caTrustStore = ca.trustStore(caTrustStorePath)

        val server = embeddedServer(engine,
            EngineSSLConnectorBuilder(
                keyAlias = "mykey",
                keyStore = serverKeys,
                keyStorePassword = { "changeit".toCharArray() },
                privateKeyPassword = { "changeit".toCharArray() },
            ).apply {
                trustStore = caTrustStore
                if (engine is Tomcat) {
                    keyStorePath = serverKeyPath
                    trustStorePath = caTrustStorePath
                }
            }
        ) {
            routing {
                get {
                    call.respondText { "Hello World" }
                }
            }
        }
        server.start()
        val client = HttpClient(CIO) {
            engine {
                https {
                    trustManager = caTrustStore.trustManagers.first()
                    addKeyStore(clientKeys, "changeit".toCharArray())
                }
            }
        }
        assertEquals("Hello World", client.get("https://0.0.0.0:443"))
        server.stop(1000, 1000)
    }
}
