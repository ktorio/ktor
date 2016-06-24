package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class CORSTest {
    @Test
    fun testSimpleRequest() {
        withTestApplication {
            application.CORS {
                host("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestHttps() {
        withTestApplication {
            application.CORS {
                host("my-host", https = true)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(setOf("http://my-host", "https://my-host"), call.response.headers[HttpHeaders.AccessControlAllowOrigin]?.let { it.split(" ") }?.toSet())
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(setOf("http://my-host", "https://my-host"), call.response.headers[HttpHeaders.AccessControlAllowOrigin]?.let { it.split(" ") }?.toSet())
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStar() {
        withTestApplication {
            application.CORS {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStarCredentials() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("true", call.response.headers[HttpHeaders.AccessControlAllowCredentials])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testPreFlight() {
        withTestApplication {
            application.CORS {
                anyHost()
                header(HttpHeaders.Range)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "PUT")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.CacheControl)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ALPN)
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Range)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
                assertTrue { HttpHeaders.Range in call.response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty() }
            }
        }
    }
}