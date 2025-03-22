/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This tests uses a CA, which creates server and client certificates.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.suites.ClientCertTestSuite)
 */
abstract class ClientCertTestSuite<Engine : ApplicationEngine, Configuration : ApplicationEngine.Configuration>(
    val engine: ApplicationEngineFactory<Engine, Configuration>
) {
    open fun sslConnectorBuilder(): EngineSSLConnectorBuilder = EngineSSLConnectorBuilder(
        keyAlias = "mykey",
        keyStore = ca.generateCertificate(keyType = KeyType.Server),
        keyStorePassword = { "changeit".toCharArray() },
        privateKeyPassword = { "changeit".toCharArray() },
    ).apply {
        trustStore = ca.trustStore()
        port = 0
    }

    companion object {
        val ca = generateCertificate(keyType = KeyType.CA)
    }

    @CoroutinesTimeout(60 * 1000, cancelOnTimeout = true)
    @Test
    open fun `Server requesting Client Certificate from CIO Client`() {
        val clientKeys = ca.generateCertificate(keyType = KeyType.Client)

        val client = HttpClient(CIO) {
            engine {
                https {
                    trustManager = ca.trustStore().trustManagers.first()
                    addKeyStore(clientKeys, "changeit".toCharArray() as CharArray?)
                }
            }
        }

        runBlocking {
            launch {
                val server = embeddedServer(factory = engine, connectors = arrayOf(sslConnectorBuilder())) {
                    routing {
                        get {
                            call.respondText { "Hello World" }
                        }
                    }
                }
                server.start(wait = false)

                val port = server.engine.resolvedConnectors().first().port

                assertEquals("Hello World", client.get("https://localhost:$port").body())
                server.stop(50, 1000)
            }
        }
    }
}
