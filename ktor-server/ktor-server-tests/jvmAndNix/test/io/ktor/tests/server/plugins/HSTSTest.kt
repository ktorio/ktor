/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.forwardedsupport.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class HSTSTest {
    @Test
    fun testHttp() {
        withTestApplication {
            application.testApp()

            handleRequest(HttpMethod.Get, "/").let { call ->
                assertNull(call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    @Test
    fun testHttps() {
        withTestApplication {
            application.testApp()

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "some")
            }.let { call ->
                assertEquals(
                    "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                    call.response.headers[HttpHeaders.StrictTransportSecurity]
                )
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }.let { call ->
                assertEquals(
                    "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                    call.response.headers[HttpHeaders.StrictTransportSecurity]
                )
            }
        }
    }

    @Test
    fun testSubrouteInstall() = withTestApplication {
        application.install(XForwardedHeaderSupport)
        application.routing {
            route("/1") {
                install(HSTS) {
                    maxAgeInSeconds = 10
                    includeSubDomains = true
                    preload = true
                    customDirectives["some"] = "va=lue"
                }
                get {
                    call.respondText("test") {
                        caching = CachingOptions(CacheControl.NoCache(null))
                    }
                }
            }
            get("/2") {
                call.respondText("test") {
                    caching = CachingOptions(CacheControl.NoCache(null))
                }
            }
        }

        handleRequest(HttpMethod.Get, "/1") {
            addHeader(HttpHeaders.XForwardedProto, "https")
            addHeader(HttpHeaders.XForwardedHost, "some")
        }.let { call ->
            assertEquals(
                "max-age=10; includeSubDomains; preload; some=\"va=lue\"",
                call.response.headers[HttpHeaders.StrictTransportSecurity]
            )
        }

        handleRequest(HttpMethod.Get, "/2").let { call ->
            assertNull(call.response.headers[HttpHeaders.StrictTransportSecurity])
        }
    }

    @Test
    fun testCustomPort() {
        withTestApplication {
            application.testApp()

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "some:8443")
            }.let { call ->
                assertNull(call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    @Test
    fun testHttpsCustomDirectiveNoValue() {
        withTestApplication {
            application.testApp {
                customDirectives.clear()
                customDirectives["some"] = null
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }.let { call ->
                assertEquals(
                    "max-age=10; includeSubDomains; preload; some",
                    call.response.headers[HttpHeaders.StrictTransportSecurity]
                )
            }
        }
    }

    @Test
    fun testHttpsNoCustomDirectives() {
        withTestApplication {
            application.testApp {
                customDirectives.clear()
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }.let { call ->
                assertEquals(
                    "max-age=10; includeSubDomains; preload",
                    call.response.headers[HttpHeaders.StrictTransportSecurity]
                )
            }
        }
    }

    @Test
    fun testHttpsMaxAgeOnly() {
        withTestApplication {
            application.testApp {
                customDirectives.clear()
                includeSubDomains = false
                preload = false
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }.let { call ->
                assertEquals("max-age=10", call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    private fun Application.testApp(block: HSTS.Configuration.() -> Unit = {}) {
        install(XForwardedHeaderSupport)
        install(HSTS) {
            maxAgeInSeconds = 10
            includeSubDomains = true
            preload = true
            customDirectives["some"] = "va=lue"

            block()
        }

        routing {
            get("/") {
                call.respond("ok")
            }
        }
    }
}
