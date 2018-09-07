package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class HttpsRedirectFeatureTest {
    @Test
    fun testRedirect() {
        withTestApplication {
            application.install(HttpsRedirect)
            application.routing {
                get("/") {
                    call.respond("ok")
                }
            }


            handleRequest(HttpMethod.Get, "/").let { call ->
                assertEquals(HttpStatusCode.MovedPermanently, call.response.status())
                assertEquals("https://localhost/", call.response.headers[HttpHeaders.Location])
            }
        }
    }

    @Test
    fun testRedirectHttps() {
        withTestApplication {
            application.install(XForwardedHeaderSupport)
            application.install(HttpsRedirect)
            application.routing {
                get("/") {
                    call.respond("ok")
                }
            }


            handleRequest(HttpMethod.Get, "/", {
                addHeader(HttpHeaders.XForwardedProto, "https")
            }).let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
            }
        }
    }

    @Test
    fun testDirectPathAndQuery() {
        withTestApplication {
            application.install(HttpsRedirect)
            application.intercept(ApplicationCallPipeline.Fallback) {
                call.respond("ok")
            }

            handleRequest(HttpMethod.Get, "/some/path?q=1").let { call ->
                assertEquals(HttpStatusCode.MovedPermanently, call.response.status())
                assertEquals("https://localhost/some/path?q=1", call.response.headers[HttpHeaders.Location])
            }
        }
    }

    @Test
    fun testDirectPathAndQueryWithCustomPort() {
        withTestApplication {
            application.install(HttpsRedirect) {
                sslPort = 8443
            }
            application.intercept(ApplicationCallPipeline.Fallback) {
                call.respond("ok")
            }

            handleRequest(HttpMethod.Get, "/some/path?q=1").let { call ->
                assertEquals(HttpStatusCode.MovedPermanently, call.response.status())
                assertEquals("https://localhost:8443/some/path?q=1", call.response.headers[HttpHeaders.Location])
            }
        }
    }
}