/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
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

internal fun startServer(scope: CoroutineScope, verbose: Boolean) {
    TestTcpServer(HTTP_PROXY_PORT, scope, ::tcpServerHandler)
    TestTcpServer(SOCKS_PROXY_PORT, scope, ::socksServerHandler)

    val servers = listOf(
        embeddedServer(CIO, DEFAULT_PORT, module = { tests(verbose) }),
        setupNettyServer(HTTP2_SERVER_PORT, DEFAULT_TLS_PORT, module = { tests(verbose) }),
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

/**
 * Netty server running the same application on two connectors:
 * - [plainPort]: HTTP/1.1 and HTTP/2 without TLS (h2c).
 * - [tlsPort]: HTTP/1.1 and HTTP/2 over TLS (negotiated via ALPN), and HTTP/3 over UDP on the same port.
 */
@OptIn(ExperimentalKtorApi::class)
@Suppress("SameParameterValue")
private fun setupNettyServer(
    plainPort: Int,
    tlsPort: Int,
    module: suspend Application.() -> Unit,
): EmbeddedServer<*, *> {
    val file = File.createTempFile("server", "certificate")
    val testKeyStore = generateCertificate(file)
    return embeddedServer(
        factory = Netty,
        configure = {
            connector { port = plainPort }
            sslConnector(
                keyStore = testKeyStore,
                keyAlias = "mykey",
                keyStorePassword = { "changeit".toCharArray() },
                privateKeyPassword = { "changeit".toCharArray() }
            ) {
                port = tlsPort
                keyStorePath = file
            }
            enableHttp2 = true
            enableH2c = true
            enableHttp3()
        },
        module = module,
    )
}
