/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
class OriginConnectionPointTest {
    @Test
    fun testDirectRequest() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.local) {
                        assertEquals("localhost", host)
                        assertEquals("localhost", localHost)
                        assertEquals("localhost", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals("localhost", localHost)
                        assertEquals("localhost", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/")
        }
    }

    @Test
    fun testProxyXForwardedFor() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals("localhost", localHost)
                        assertEquals("localhost", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedFor, "client, proxy")
            }
        }
    }

    @Test
    fun testProxyXForwardedHostNoPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("host", serverHost)
                        assertEquals("localhost", localHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedHost, "host")
            }
        }
    }

    @Test
    fun testProxyXForwardedHostWithPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(90, port)
                        assertEquals(80, localPort)
                        assertEquals(90, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedHost, "host:90")
            }
        }
    }

    @Test
    fun testProxyXForwardedScheme() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals("localhost", localHost)
                        assertEquals("localhost", serverHost)
                        assertEquals(443, port)
                        assertEquals(80, localPort)
                        assertEquals(443, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }
        }
    }

    @Test
    fun testProxyXForwardedSchemeWithPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(90, port)
                        assertEquals(80, localPort)
                        assertEquals(90, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "host:90")
            }
        }
    }

    @Test
    fun testProxyXForwardedSchemeNoPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(443, port)
                        assertEquals(80, localPort)
                        assertEquals(443, serverPort)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "host")
            }
        }
    }

    @Test
    fun testProxyXForwardedPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-Port", "91")
            }
        }
    }

    @Test
    fun testProxyXForwardedSchemeWithPortAndXForwardedPort() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedHost, "host:90")
                addHeader("X-Forwarded-Port", "91")
            }
        }
    }

    @Test
    fun testProxyXForwardedHttpsFlagOn() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(443, port)
                        assertEquals(80, localPort)
                        assertEquals(443, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on")
            }
        }
    }

    @Test
    fun testProxyXForwardedTakeFirstValueByDefault() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host1", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on,of")
                addHeader(HttpHeaders.XForwardedFor, "client, proxy")
                addHeader(HttpHeaders.XForwardedProto, "https,http")
                addHeader("X-Forwarded-Port", "91,92")
                addHeader(HttpHeaders.XForwardedHost, "host1, host2")
            }
        }
    }

    @Test
    fun testProxyXForwardedTakeLastValue() {
        withTestApplication {
            server.install(XForwardedHeaders) {
                useLastProxy()
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host1", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "of,on")
                addHeader(HttpHeaders.XForwardedFor, "proxy,client")
                addHeader(HttpHeaders.XForwardedProto, "http,https")
                addHeader("X-Forwarded-Port", "92,91")
                addHeader(HttpHeaders.XForwardedHost, "host2, host1")
            }
        }
    }

    @Test
    fun testProxyXForwardedSkipLastProxies() {
        withTestApplication {
            server.install(XForwardedHeaders) {
                skipLastProxies(2)
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host1", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on,of,of")
                addHeader(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
                addHeader(HttpHeaders.XForwardedProto, "https,http,http")
                addHeader("X-Forwarded-Port", "91,92,93")
                addHeader(HttpHeaders.XForwardedHost, "host1, host2, host3")
            }
        }
    }

    @Test
    fun testProxyXForwardedSkipLastProxiesTakesLastIfNotEnoughValues() {
        withTestApplication {
            server.install(XForwardedHeaders) {
                skipLastProxies(4)
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(93, port)
                        assertEquals(80, localPort)
                        assertEquals(93, serverPort)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("proxy2", remoteHost)
                        assertEquals("host3", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host3", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on,of,of")
                addHeader(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
                addHeader(HttpHeaders.XForwardedProto, "https,http,http")
                addHeader("X-Forwarded-Port", "91,92,93")
                addHeader(HttpHeaders.XForwardedHost, "host1, host2, host3")
            }
        }
    }

    @Test
    fun testProxyXForwardedSkipKnownProxiesAllValues() {
        withTestApplication {
            server.install(XForwardedHeaders) {
                skipKnownProxies(listOf("proxy", "proxy2"))
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host1", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on,of,of")
                addHeader(HttpHeaders.XForwardedFor, "client, proxy, proxy2")
                addHeader(HttpHeaders.XForwardedProto, "https,http,http")
                addHeader("X-Forwarded-Port", "91,92,93")
                addHeader(HttpHeaders.XForwardedHost, "host1, host2, host3")
            }
        }
    }

    @Test
    fun testProxyXForwardedSkipKnownProxiesMissingValues() {
        withTestApplication {
            server.install(XForwardedHeaders) {
                skipKnownProxies(listOf("missing", "proxy2"))
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host1", serverHost)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-SSL", "on,of")
                addHeader(HttpHeaders.XForwardedFor, "client, proxy2")
                addHeader(HttpHeaders.XForwardedProto, "https,http")
                addHeader("X-Forwarded-Port", "91,93")
                addHeader(HttpHeaders.XForwardedHost, "host1, host3")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHost() {
        withTestApplication {
            server.install(ForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostAndPort() {
        withTestApplication {
            server.install(ForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(90, port)
                        assertEquals(80, localPort)
                        assertEquals(90, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host:90")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostAndProto() {
        withTestApplication {
            server.install(ForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(443, port)
                        assertEquals(80, localPort)
                        assertEquals(443, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;proto=https;host=host")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCNoHost() {
        withTestApplication {
            server.install(ForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals("localhost", localHost)
                        assertEquals("localhost", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostMultiple() {
        withTestApplication {
            server.install(ForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals("localhost", localHost)
                        assertEquals("host", serverHost)
                        assertEquals(80, port)
                        assertEquals(80, localPort)
                        assertEquals(80, serverPort)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host,for=proxy;host=internal-host")
            }
        }
    }

    @Test
    fun testProxyForwardedTakeFirstValue() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                useFirstValue()
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host,for=proxy;host=internal-host")
            }
        }
    }

    @Test
    fun testProxyForwardedTakeLastValue() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                useLastValue()
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=proxy;host=internal-host,for=client;host=host")
            }
        }
    }

    @Test
    fun testProxyForwardedSkipLastProxies() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                skipLastProxies(2)
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Forwarded,
                    "for=client;host=host,for=proxy;host=internal-host,for=proxy1;host=internal-host-1"
                )
            }
        }
    }

    @Test
    fun testProxyForwardedSkipLastProxiesTakesLastIfNotEnoughValues() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                skipLastProxies(4)
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("internal-host-1", host)
                        assertEquals(80, port)
                        assertEquals("proxy1", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Forwarded,
                    "for=client;host=host,for=proxy;host=internal-host,for=proxy1;host=internal-host-1"
                )
            }
        }
    }

    @Test
    fun testProxyForwardedSkipKnownProxiesAllValues() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                skipKnownProxies(listOf("proxy", "proxy2"))
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(
                    HttpHeaders.Forwarded,
                    "for=client;host=host,for=proxy;host=internal-host,for=proxy2;host=internal-host-1"
                )
            }
        }
    }

    @Test
    fun testProxyForwardedSkipKnownProxiesMissingValues() {
        withTestApplication {
            server.install(ForwardedHeaders) {
                skipKnownProxies(listOf("missing", "proxy2"))
            }
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host,for=proxy2;host=internal-host")
            }
        }
    }

    @Test
    fun testOriginWithNoPlugins(): Unit = withTestApplication {
        server.routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                }

                call.respond("OK")
            }
            get("/90") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals("localhost", localHost)
                    assertEquals("host", serverHost)
                    assertEquals(90, port)
                    assertEquals(80, localPort)
                    assertEquals(90, serverPort)
                }

                call.respond("OK")
            }
            get("/no-header") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals("localhost", localHost)
                    assertEquals("localhost", serverHost)
                    assertEquals(80, port)
                    assertEquals(80, localPort)
                    assertEquals(80, serverPort)
                }

                call.respond("OK")
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Host, "host")
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Host, "host:80")
        }

        handleRequest(HttpMethod.Get, "/90") {
            addHeader(HttpHeaders.Host, "host:90")
        }

        handleRequest(HttpMethod.Get, "/no-header") {
        }
    }

    @Test
    fun testProxyXForwardedPortList() {
        withTestApplication {
            server.install(XForwardedHeaders)
            server.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals(80, localPort)
                        assertEquals(91, serverPort)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-Port", "91, 90,95")
            }
        }
    }

    @Test
    fun testXForwardedHeadersSetRemoteAddress() = testServer {
        application {
            install(XForwardedHeaders)

            routing {
                get("/ipv4-address") {
                    assertEquals("192.168.0.1", call.request.origin.remoteAddress)
                    assertEquals("192.168.0.1", call.request.origin.remoteHost)
                }
                get("/ipv6-address") {
                    assertEquals("2001:db8::1", call.request.origin.remoteAddress)
                    assertEquals("2001:db8::1", call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ipv4-address") {
            header(HttpHeaders.XForwardedFor, "192.168.0.1")
        }
        client.get("/ipv6-address") {
            header(HttpHeaders.XForwardedFor, "2001:db8::1")
        }
    }

    @Test
    fun testForwardedHeadersSetRemoteAddress() = testServer {
        application {
            install(ForwardedHeaders)

            routing {
                get("/ipv4-address") {
                    assertEquals("192.168.0.1", call.request.origin.remoteAddress)
                    assertEquals("192.168.0.1", call.request.origin.remoteHost)
                }
                get("/ipv6-address") {
                    assertEquals("2001:db8::1", call.request.origin.remoteAddress)
                    assertEquals("2001:db8::1", call.request.origin.remoteHost)
                }
            }
        }

        client.get("/ipv4-address") {
            header(HttpHeaders.Forwarded, "for=192.168.0.1")
        }
        client.get("/ipv6-address") {
            header(HttpHeaders.Forwarded, "for=2001:db8::1")
        }
    }
}
