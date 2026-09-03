/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3StartupTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    private fun http3Server(
        configureEngine: NettyApplicationEngine.Configuration.() -> Unit,
        module: Application.() -> Unit = {},
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val properties = serverConfig(applicationEnvironment { }) { module(module) }
        return embeddedServer(Netty, properties, configureEngine)
    }

    private fun NettyApplicationEngine.Configuration.testSslConnector(sslConnectorPort: Int) {
        sslConnector(
            keyStore,
            "mykey",
            { "changeit".toCharArray() },
            { "changeit".toCharArray() }
        ) {
            port = sslConnectorPort
            keyStorePath = keyStoreFile.absoluteFile
        }
    }

    @Test
    fun `enableHttp3 without an SSL connector fails with the documented message`() = runTest {
        val server = http3Server({
            connector { port = this@NettyHttp3StartupTest.port }
            enableHttp3()
        })

        val failure = assertFailsWith<IllegalArgumentException> { server.start(wait = false) }
        assertEquals(
            "Netty HTTP/3 requires at least one SSL connector. Add an SSL connector or disable enableHttp3.",
            failure.message
        )
        server.stop(0, 0)
    }

    /**
     * With `port = 0` the SSL connector resolves an ephemeral port, and the UDP listener must bind
     * that resolved port — otherwise HTTP/3 would answer somewhere other than where
     * `resolvedConnectors` says the server is listening.
     */
    @Test
    fun `an ephemeral port is resolved and the UDP listener binds it`() = runTest {
        val server = http3Server(
            configureEngine = {
                testSslConnector(0)
                enableHttp3()
            },
            module = {
                routing { get("/resolved") { call.respondText("ok") } }
            }
        )

        try {
            server.start(wait = false)
            val resolved = server.engine.resolvedConnectors().single().port
            assertNotEquals(0, resolved, "the connector must report a resolved port")

            withHttp3Client(resolved) { connection ->
                assertEquals("ok", connection.request(path = "/resolved").bodyText)
            }
        } finally {
            server.stop(0, 500)
        }
    }

    /** A UDP port already in use must fail the start rather than half-starting the engine. */
    @Test
    fun `a UDP port already in use fails the start and leaves the first server serving`() = runTest {
        createAndStartServer {
            get("/first") { call.respondText("first") }
        }

        val second = http3Server({
            testSslConnector(sslPort)
            enableHttp3()
        })

        try {
            assertFailsWith<Exception> { second.start(wait = false) }
        } finally {
            second.stop(0, 500)
        }

        withHttp3Client(sslPort) { connection ->
            assertEquals("first", connection.request(path = "/first").bodyText)
        }
    }
}
