/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedsupport.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class OriginConnectionPointTest {
    @Test
    fun testDirectRequest() {
        withTestApplication {
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.local) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                    }

                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(80, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(90, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals(443, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(90, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(443, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(443, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
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
            application.install(XForwardedHeaderSupport) {
                useLastProxy()
            }
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
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
            application.install(XForwardedHeaderSupport) {
                skipLastProxies(2)
            }
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
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
            application.install(XForwardedHeaderSupport) {
                skipLastProxies(4)
            }
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(93, port)
                        assertEquals("http", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("proxy2", remoteHost)
                        assertEquals("host3", host)
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
            application.install(XForwardedHeaderSupport) {
                skipKnownProxies(listOf("proxy", "proxy2"))
            }
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
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
            application.install(XForwardedHeaderSupport) {
                skipKnownProxies(listOf("missing", "proxy2"))
            }
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                        assertEquals("https", scheme)
                        assertEquals("HTTP/1.1", version)
                        assertEquals("client", remoteHost)
                        assertEquals("host1", host)
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
            application.install(ForwardedHeaderSupport)
            application.routing {
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
                addHeader(HttpHeaders.Forwarded, "for=client;host=host")
            }
        }
    }

    @Test
    fun testProxyForwardedPerRFCWithHostAndPort() {
        withTestApplication {
            application.install(ForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(90, port)
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
            application.install(ForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("host", host)
                        assertEquals(443, port)
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
            application.install(ForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
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
            application.install(ForwardedHeaderSupport)
            application.routing {
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
    fun testProxyForwardedTakeFirstValue() {
        withTestApplication {
            application.install(ForwardedHeaderSupport) {
                useFirstValue()
            }
            application.routing {
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
            application.install(ForwardedHeaderSupport) {
                useLastValue()
            }
            application.routing {
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
            application.install(ForwardedHeaderSupport) {
                skipLastProxies(2)
            }
            application.routing {
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
            application.install(ForwardedHeaderSupport) {
                skipLastProxies(4)
            }
            application.routing {
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
            application.install(ForwardedHeaderSupport) {
                skipKnownProxies(listOf("proxy", "proxy2"))
            }
            application.routing {
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
            application.install(ForwardedHeaderSupport) {
                skipKnownProxies(listOf("missing", "proxy2"))
            }
            application.routing {
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
        application.routing {
            get("/") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(80, port)
                }

                call.respond("OK")
            }
            get("/90") {
                with(call.request.origin) {
                    assertEquals("host", host)
                    assertEquals(90, port)
                }

                call.respond("OK")
            }
            get("/no-header") {
                with(call.request.origin) {
                    assertEquals("localhost", host)
                    assertEquals(80, port)
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(91, port)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader("X-Forwarded-Port", "91, 90,95")
            }
        }
    }
}
