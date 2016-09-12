package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
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
            application.install(XForwardedHeadersSupport)
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
            application.intercept(ApplicationCallPipeline.Fallback) { call ->
                call.respond("ok")
            }

            handleRequest(HttpMethod.Get, "/some/path?q=1").let { call ->
                assertEquals(HttpStatusCode.MovedPermanently, call.response.status())
                assertEquals("https://localhost/some/path?q=1", call.response.headers[HttpHeaders.Location])
            }
        }
    }
}