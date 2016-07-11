package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class OriginRouteTest {
    @Test
    fun testDirectRequest() {
        withTestApplication {
            application.routing {
                get("/") {
                    with(call.request.actualRoute) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
                    }

                    with(call.request.originRoute) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(90, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("localhost", host)
                        assertEquals(443, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(90, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(443, port)
                        assertEquals("localhost", remoteHost)
                        assertEquals("https", scheme)
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
    fun testProxyForwardedPerRFCWithHost() {
        withTestApplication {
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(90, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(443, port)
                        assertEquals("client", remoteHost)
                        assertEquals("https", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("localhost", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
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
            application.routing {
                get("/") {
                    with(call.request.originRoute) {
                        assertEquals("host", host)
                        assertEquals(80, port)
                        assertEquals("client", remoteHost)
                        assertEquals("http", scheme)
                    }

                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Forwarded, "for=client;host=host,for=proxy;host=internal-host")
            }
        }
    }
}