package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.time.*
import kotlin.test.*

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

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "some")
            }).let { call ->
                assertEquals("max-age=10; includeSubDomains; preload; some=\"va=lue\"", call.response.headers[HttpHeaders.StrictTransportSecurity])
            }

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }).let { call ->
                assertEquals("max-age=10; includeSubDomains; preload; some=\"va=lue\"", call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    @Test
    fun testCustomPort() {
        withTestApplication {
            application.testApp()

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
                addHeader(HttpHeaders.XForwardedHost, "some:8443")
            }).let { call ->
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

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }).let { call ->
                assertEquals("max-age=10; includeSubDomains; preload; some", call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    @Test
    fun testHttpsNoCustomDirectives() {
        withTestApplication {
            application.testApp {
                customDirectives.clear()
            }

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }).let { call ->
                assertEquals("max-age=10; includeSubDomains; preload", call.response.headers[HttpHeaders.StrictTransportSecurity])
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

            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }).let { call ->
                assertEquals("max-age=10", call.response.headers[HttpHeaders.StrictTransportSecurity])
            }
        }
    }

    private fun Application.testApp(block: HSTS.Configuration.() -> Unit = {}) {
        install(XForwardedHeaderSupport)
        install(HSTS) {
            maxAge = Duration.ofSeconds(10L)
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
