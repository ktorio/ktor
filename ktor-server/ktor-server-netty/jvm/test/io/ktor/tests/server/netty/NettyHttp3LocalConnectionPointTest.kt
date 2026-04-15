/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.server.netty.http.*
import io.netty.handler.codec.http3.*
import java.net.*
import kotlin.test.*

@Suppress("DEPRECATION_ERROR")
class NettyHttp3LocalConnectionPointTest {
    @Test
    fun `test method`() {
        val point = point {
            method("PUT")
        }

        assertEquals(HttpMethod.Put, point.method)
    }

    @Test
    fun `test version is HTTP 3`() {
        val point = point {
            method("PUT")
        }

        assertEquals("HTTP/3", point.version)
    }

    @Test
    fun `test scheme`() {
        val point = point {
            scheme("https")
        }

        assertEquals("https", point.scheme)
    }

    @Test
    fun `test scheme missing defaults to http`() {
        val point = point {
        }

        assertEquals("http", point.scheme)
    }

    @Test
    fun `test port from authority`() {
        val point = point {
            authority("host:443")
        }

        assertEquals(443, point.port)
    }

    @Test
    fun `test port unspecified defaults to 80`() {
        val point = point {
            authority("host")
        }

        assertEquals(80, point.port)
    }

    @Test
    fun `test host from authority`() {
        val point = point {
            authority("host1")
        }

        assertEquals("host1", point.host)
    }

    @Test
    fun `test host with port from authority`() {
        val point = point {
            authority("host1:80")
        }

        assertEquals("host1", point.host)
    }

    @Test
    fun `test host missing defaults to localhost`() {
        val point = point {
        }

        assertEquals("localhost", point.host)
    }

    @Test
    fun `test uri from path`() {
        val point = point {
            path("/path/to/resource")
        }

        assertEquals("/path/to/resource", point.uri)
    }

    @Test
    fun `test uri missing defaults to root`() {
        val point = point {
        }

        assertEquals("/", point.uri)
    }

    @Test
    fun `test remote address`() {
        val address = InetSocketAddress.createUnresolved("some-host", 8554)
        val point = point(remoteAddress = address) {
        }

        assertEquals("some-host", point.remoteHost)
        assertTrue(address.isUnresolved)
    }

    @Test
    fun `test remote address resolved`() {
        val address = InetSocketAddress(
            Inet4Address.getByAddress("z", byteArrayOf(192.toByte(), 168.toByte(), 1, 1)),
            7777
        )
        val point = point(remoteAddress = address) {
        }

        assertEquals("z", point.remoteHost)
    }

    @Test
    fun `test local host and port`() {
        val point = point(
            remoteAddress = InetSocketAddress("remote", 123),
            localAddress = InetSocketAddress("local", 234),
        ) {
            authority("host1:80")
        }
        assertEquals("local", point.localHost)
        assertEquals(234, point.localPort)
    }

    @Test
    fun `test server host and port`() {
        val point = point(
            remoteAddress = InetSocketAddress("remote", 123),
            localAddress = InetSocketAddress("local", 234),
        ) {
            authority("host1:81")
        }
        assertEquals("host1", point.serverHost)
        assertEquals(81, point.serverPort)
    }

    @Test
    fun `test server host and port no header`() {
        val point = point(
            remoteAddress = InetSocketAddress("remote", 123),
            localAddress = InetSocketAddress("local", 234),
        ) {}
        assertEquals("local", point.serverHost)
        assertEquals(234, point.serverPort)
    }

    private fun point(
        localAddress: InetSocketAddress? = null,
        remoteAddress: InetSocketAddress? = null,
        block: DefaultHttp3Headers.() -> Unit
    ): HttpMultiplexedConnectionPoint {
        val headers = DefaultHttp3Headers().apply(block)
        return HttpMultiplexedConnectionPoint(
            pseudoMethod = headers.method(),
            pseudoScheme = headers.scheme(),
            pseudoAuthority = headers.authority(),
            pseudoPath = headers.path(),
            localNetworkAddress = localAddress,
            remoteNetworkAddress = remoteAddress,
            httpVersion = "HTTP/3",
        )
    }
}
