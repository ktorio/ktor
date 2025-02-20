/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.network.tls.certificates.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import test.server.tests.CloseableGroup
import test.server.tests.tcpServerHandler
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

const val TEST_SERVER: String = "http://127.0.0.1:8080"

private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_TLS_PORT: Int = 8089
private const val HTTP_PROXY_PORT: Int = 8082

internal fun startServer(): Closeable {
    val scope = CloseableGroup()
    try {
        val tcpServer = TestTcpServer(HTTP_PROXY_PORT, ::tcpServerHandler)
        scope.use(tcpServer)

        val server = embeddedServer(CIO, DEFAULT_PORT) {
            tests()
        }.start()

        scope.use { server.stop(0L, 0L, TimeUnit.MILLISECONDS) }

        val tlsServer = setupTLSServer()
        tlsServer.start()
        scope.use { tlsServer.stop(0L, 0L, TimeUnit.MILLISECONDS) }

        Thread.sleep(1000)
    } catch (cause: Throwable) {
        scope.close()
    }

    return scope
}

private fun setupTLSServer(): EmbeddedServer<*, *> {
    val file = File.createTempFile("server", "certificate")
    val testKeyStore = generateCertificate(file)
    val tlsServer = embeddedServer(Jetty, configure = {
        sslConnector(
            testKeyStore,
            "mykey",
            { "changeit".toCharArray() },
            { "changeit".toCharArray() },
            {
                this.port = DEFAULT_TLS_PORT
                this.keyStorePath = file
            }
        )
    }) {
        tlsTests()
    }

    return tlsServer
}
