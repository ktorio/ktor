/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

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
    fun testOriginWithNoFeatures(): Unit = withTestApplication {
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
}
