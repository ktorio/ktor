package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
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
                    }

                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
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
    fun testProxyXForwardedHttpsFlagOn() {
        withTestApplication {
            application.install(XForwardedHeaderSupport)
            application.routing {
                get("/") {
                    with(call.request.origin) {
                        assertEquals(443, port)
                        assertEquals("https", scheme)
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