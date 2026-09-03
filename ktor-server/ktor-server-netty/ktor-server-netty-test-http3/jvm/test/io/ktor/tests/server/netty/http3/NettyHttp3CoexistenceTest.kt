/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpProtocolVersion
import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.httpVersion
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 2 case F: HTTP/1.1, HTTP/2 and HTTP/3 served by one server at the same time.
 *
 * HTTP/3 binds UDP on the port its SSL connector already listens on for TCP, so the two stacks
 * share a port number while using different transports. Nothing previously checked that they work
 * side by side, or that HTTP/3 follows along when there is more than one SSL connector.
 */
@OptIn(ExperimentalKtorApi::class)
class NettyHttp3CoexistenceTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp2 = true
        enableHttp3 = true
        // This class compares the protocols against each other, so it needs the TCP legs too.
        http3Only = false
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `HTTP1 HTTP2 and HTTP3 are served concurrently on the same ssl port`() = runTest {
        createAndStartServer {
            get("/version") { call.respondText(call.request.httpVersion) }
        }

        // Issue all four legs at once: TCP and UDP on the same port number must not interfere.
        coroutineScope {
            val plainHttp1 = async(Dispatchers.IO) {
                var seen = ""
                withHttp1("http://127.0.0.1:$port/version", port, {}) { seen = bodyAsText() }
                seen
            }
            val sslHttp1 = async(Dispatchers.IO) {
                var seen = ""
                withHttp1("https://127.0.0.1:$sslPort/version", sslPort, {}) { seen = bodyAsText() }
                seen
            }
            val http2 = async(Dispatchers.IO) {
                var seen: HttpProtocolVersion? = null
                withHttp2("https://127.0.0.1:$sslPort/version", sslPort, {}) { seen = version }
                seen
            }
            val http3 = async(Dispatchers.IO) {
                withHttp3Client(sslPort) { connection ->
                    connection.request(path = "/version").bodyText
                }
            }

            assertEquals("HTTP/1.1", plainHttp1.await(), "the plain TCP connector must still serve HTTP/1.1")
            assertEquals("HTTP/1.1", sslHttp1.await(), "TLS over TCP on the ssl port must still serve HTTP/1.1")
            assertEquals(HttpProtocolVersion.HTTP_2_0, http2.await(), "HTTP/2 must still negotiate over TCP")
            assertEquals("HTTP/3", http3.await(), "HTTP/3 must be served over UDP on the same port")
        }
    }

    @Test
    fun `the TCP and UDP sides of one port stay independent under interleaved load`() = runTest {
        createAndStartServer {
            get("/version") { call.respondText(call.request.httpVersion) }
        }

        coroutineScope {
            val calls = (1..6).flatMap {
                listOf(
                    async(Dispatchers.IO) {
                        var seen = ""
                        withHttp1("https://127.0.0.1:$sslPort/version", sslPort, {}) { seen = bodyAsText() }
                        seen
                    },
                    async(Dispatchers.IO) {
                        withHttp3Client(sslPort) { connection ->
                            connection.request(path = "/version").bodyText
                        }
                    },
                )
            }

            val versions = calls.awaitAll()
            assertEquals(6, versions.count { it == "HTTP/1.1" }, "every TCP call must be answered")
            assertEquals(6, versions.count { it == "HTTP/3" }, "every UDP call must be answered")
        }
    }
}

/**
 * Case F: HTTP/3 has to follow every SSL connector, not just the first one, and has to work on an
 * IPv6 and a wildcard bind.
 */
@OptIn(ExperimentalKtorApi::class)
class NettyHttp3ConnectorMatrixTest :
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
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val module: Application.() -> Unit = {
            routing { get("/where") { call.respondText("served") } }
        }
        val properties = serverConfig(applicationEnvironment { }) { module(module) }
        return embeddedServer(Netty, properties, configureEngine)
    }

    private fun NettyApplicationEngine.Configuration.testSslConnector(
        connectorPort: Int,
        connectorHost: String = "0.0.0.0",
    ) {
        sslConnector(
            keyStore,
            "mykey",
            { "changeit".toCharArray() },
            { "changeit".toCharArray() }
        ) {
            host = connectorHost
            port = connectorPort
            keyStorePath = keyStoreFile.absoluteFile
        }
    }

    @Test
    fun `HTTP3 binds every ssl connector`() = runTest {
        val secondPort = findFreeUdpPort()

        val server = http3Server {
            testSslConnector(sslPort)
            testSslConnector(secondPort)
            enableHttp3()
        }

        try {
            server.start(wait = false)

            for (boundPort in listOf(sslPort, secondPort)) {
                withHttp3Client(boundPort) { connection ->
                    assertEquals(
                        "served",
                        connection.request(path = "/where").bodyText,
                        "HTTP/3 must be reachable on ssl connector port $boundPort"
                    )
                }
            }
        } finally {
            server.stop(0, 1000, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `HTTP3 serves an IPv6 loopback bind`() = runTest {
        assumeTrue(hasIpv6Loopback(), "no IPv6 loopback address is configured")

        val server = http3Server {
            testSslConnector(sslPort, connectorHost = "::1")
            enableHttp3()
        }

        try {
            server.start(wait = false)

            withHttp3Client(sslPort, host = "::1", authority = "[::1]:$sslPort") { connection ->
                assertEquals("served", connection.request(path = "/where").bodyText)
            }
        } finally {
            server.stop(0, 1000, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `HTTP3 serves an IPv6 wildcard bind from an IPv4 client`() = runTest {
        assumeTrue(hasIpv6Loopback(), "no IPv6 loopback address is configured")

        // "::" accepts IPv4-mapped traffic on a dual-stack host, which is how a server bound to the
        // IPv6 wildcard ends up answering IPv4 clients.
        val server = http3Server {
            testSslConnector(sslPort, connectorHost = "::")
            enableHttp3()
        }

        try {
            server.start(wait = false)

            withHttp3Client(sslPort, host = "::1", authority = "[::1]:$sslPort") { connection ->
                assertEquals("served", connection.request(path = "/where").bodyText)
            }
        } finally {
            server.stop(0, 1000, TimeUnit.MILLISECONDS)
        }
    }

    private fun hasIpv6Loopback(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .any { address: InetAddress -> address is Inet6Address && address.isLoopbackAddress }
    }.getOrDefault(false)
}
