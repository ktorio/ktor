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
import io.ktor.server.cio.CIO as CIOServer
import io.ktor.server.tomcat.*
import kotlinx.coroutines.*
import org.slf4j.*
import java.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.test.*

class TLSClientAuthenticationTest {

    @Test
    fun `Jetty Server requesting Client Certificate from CIO Client`() {
        `Server requesting Client Certificate from CIO Client`(Jetty)
    }

    @Test
    fun `Netty Server requesting Client Certificate from CIO Client`()  {
        `Server requesting Client Certificate from CIO Client`(Netty)
    }

    @Test
    fun `CIO Server requesting Client Certificate from CIO Client`() {
        val httpsNotImplemented= assertFailsWith<UnsupportedOperationException> {
            `Server requesting Client Certificate from CIO Client`(CIOServer)
        }
    }

    @Test
    fun `Tomcat Server requesting Client Certificate from CIO Client`()  {
        `Server requesting Client Certificate from CIO Client`(Tomcat)
    }

    private fun <TEngine : ApplicationEngine,
        TConfiguration : ApplicationEngine.Configuration,
        Factory : ApplicationEngineFactory<TEngine, TConfiguration>>
        `Server requesting Client Certificate from CIO Client`(engine: Factory) = runBlocking {
        val ca = generateCertificate(File.createTempFile("caKeys", "jks"), isCA = true)
        val serverKeyPath = File.createTempFile("server", "jks")
        val serverKeys = ca.generateCertificate(serverKeyPath)
        val clientKeys = ca.generateCertificate(File.createTempFile("client", "jks"))

        val caTrustStorePath = File.createTempFile("trustStore", "jks")
        val caTrustStore = ca.trustStore(caTrustStorePath)

        val server = embeddedServer(
            engine, connectors = listOf(
                EngineSSLConnectorBuilder(
                    keyAlias = "mykey",
                    keyStore = serverKeys,
                    keyStorePassword = { "changeit".toCharArray() },
                    privateKeyPassword = { "changeit".toCharArray() },
                ).apply {
                    this.host = "0.0.0.0"
                    this.port = 443
                    trustStore = caTrustStore
                    if(engine is Tomcat) {
                        keyStorePath = serverKeyPath
                        trustStorePath = caTrustStorePath
                    }
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
                    trustManager = caTrustStore.trustManagers.first()
                    addKeyStore(clientKeys, "changeit".toCharArray())
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
