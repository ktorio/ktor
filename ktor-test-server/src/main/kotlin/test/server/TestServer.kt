/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import test.server.tests.socksServerHandler
import test.server.tests.tcpServerHandler
import java.io.File

const val TEST_SERVER: String = "http://127.0.0.1:8080"

private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_TLS_PORT: Int = 8089
private const val HTTP_PROXY_PORT: Int = 8082
private const val SOCKS_PROXY_PORT: Int = 8083
private const val HTTP2_SERVER_PORT: Int = 8084

internal fun startServer(scope: CoroutineScope) {
    TestTcpServer(HTTP_PROXY_PORT, scope, ::tcpServerHandler)
    TestTcpServer(SOCKS_PROXY_PORT, scope, ::socksServerHandler)

    val servers = listOf(
        embeddedServer(CIO, DEFAULT_PORT, module = Application::tests),
        setupHttp2Server(HTTP2_SERVER_PORT, module = Application::tests),
        setupTLSServer(DEFAULT_TLS_PORT, module = Application::tlsTests),
    )

    scope.launch(CoroutineName("server-stopper")) {
        try {
            awaitCancellation()
        } finally {
            servers.forEach { it.stop(gracePeriodMillis = 0, timeoutMillis = 0) }
        }
    }

    runBlocking {
        servers.map { async { it.start() } }
            .awaitAll()
    }
}

private fun setupTLSServer(
    @Suppress("SameParameterValue") port: Int,
    module: suspend Application.() -> Unit,
): EmbeddedServer<*, *> {
    val file = File.createTempFile("server", "certificate")
    val testKeyStore = generateCertificate(file)
    val tlsServer = embeddedServer(
        factory = Jetty,
        configure = {
            sslConnector(
                keyStore = testKeyStore,
                keyAlias = "mykey",
                keyStorePassword = { "changeit".toCharArray() },
                privateKeyPassword = { "changeit".toCharArray() }
            ) {
                this.port = port
                this.keyStorePath = file
            }
        },
        module = module,
    )

    return tlsServer
}

private fun setupHttp2Server(
    @Suppress("SameParameterValue") port: Int,
    module: suspend Application.() -> Unit,
): EmbeddedServer<*, *> = embeddedServer(
    factory = Netty,
    configure = {
        connector { this.port = port }
        enableHttp2 = true
        enableH2c = true
    },
    module = module,
)
