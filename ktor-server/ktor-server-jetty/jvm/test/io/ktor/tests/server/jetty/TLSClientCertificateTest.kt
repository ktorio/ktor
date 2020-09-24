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
import kotlinx.coroutines.*
import org.slf4j.*
import java.io.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.test.*

class TLSClientCertificateTest {

    @Test
    fun `Jetty Server requesting Client Certificate from CIO Client`() = runBlocking {
        val keyStore = generateCertificateChain(File.createTempFile("test", "certificate"))

        val server = embeddedServer(
            Jetty, connectors = listOf(
                EngineSSLConnectorBuilder(
                    keyAlias = "mykey",
                    keyStore = keyStore,
                    keyStorePassword = { "changeit".toCharArray() },
                    privateKeyPassword = { "changeit".toCharArray() },
                ).apply {
                    this.host = "0.0.0.0"
                    this.port = 443
                })
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
                    trustManager = keyStore.trustManagers.first()
                    addKeyStore(keyStore, "changeit".toCharArray())
                }
            }
        }
        assertEquals("Hello World", client.get("https://0.0.0.0:443"))
        server.stop(1000, 1000)
    }
}

private val KeyStore.trustManagers: List<TrustManager>
    get() = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(this@trustManagers) }.trustManagers.toList()

private fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    CoroutineScope.embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    connectors: List<EngineConnectorConfig> = listOf(EngineConnectorBuilder().apply {
        this.port = port
        this.host = host
    }),
    watchPaths: List<String> = emptyList(),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    configure: TConfiguration.() -> Unit = {},
    module: Application.() -> Unit
): TEngine {
    val environment = applicationEngineEnvironment {
        this.parentCoroutineContext = coroutineContext + parentCoroutineContext
        this.log = LoggerFactory.getLogger("ktor.application")
        this.watchPaths = watchPaths
        this.module(module)
        this.connectors.addAll(connectors)
    }

    return embeddedServer(factory, environment, configure)
}
