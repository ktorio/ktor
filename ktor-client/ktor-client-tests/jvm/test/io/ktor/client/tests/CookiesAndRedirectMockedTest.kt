/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.test.*

class CookiesAndRedirectMockedTest {
    @Test
    fun testRequestCookieIsSentWithoutCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                assertEquals("test=value", request.headers[HttpHeaders.Cookie])
                respondOk()
            }
        }

        test { client ->
            client.get<HttpResponse>("/example") {
                cookie("test", "value")
            }
        }
    }

    @Test
    fun testRequestCookieCannotBeExpiredAndIsSentWhenRedirectedWithoutCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/redirect" -> {
                        val expiredCookie = Cookie("test", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertEquals("test=value", request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            followRedirects = true
        }

        test { client ->
            client.get<String>("/example/redirect") {
                cookie("test", "value")
            }.let { assertEquals("redirected", it) }
        }
    }

    @Test
    fun testRequestCookieIsSentWithCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                assertEquals("test=value", request.headers[HttpHeaders.Cookie])
                respondOk()
            }
            install(HttpCookies)
        }

        test { client ->
            client.get("/example") {
                cookie("test", "value")
            }
        }
    }

    @Test
    fun testRequestCookieCanBeExpiredAndIsNotSentWhenRedirectedWithCookiesFeature() = testWithEngine(MockEngine) {
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/redirect" -> {
                        val expiredCookie = Cookie("test", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertNull(request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            install(HttpCookies)
            followRedirects = true
        }

        test { client ->
            client.get<String>("/example/redirect") {
                cookie("test", "value")
            }.let { assertEquals("redirected", it) }
        }
    }

    @Test
    fun testStorageCookieCanBeExpiredAndIsNotSentWhenRedirectedWithCookiesFeature() = testWithEngine(MockEngine) {
        val storage = AcceptAllCookiesStorage()
        config {
            server { request ->
                when (request.url.fullPath) {
                    "/example/redirect" -> {
                        val expiredCookie = Cookie("test", "", expires = GMTDate.START)
                        respondRedirectWithCookie("/example", expiredCookie)
                    }
                    "/example" -> {
                        assertNull(request.headers[HttpHeaders.Cookie])
                        respondOk("redirected")
                    }
                    else -> fail("Unexpected destination")
                }
            }
            install(HttpCookies) {
                this.storage = storage
            }
            followRedirects = true
        }

        test { client ->
            storage.addCookie("/example", Cookie("test", "value"))
            assertEquals("redirected", client.get("/example/redirect"))
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.server(handler: MockRequestHandler) {
        engine {
            addHandler(handler)
        }
    }

    private fun MockRequestHandleScope.respondRedirectWithCookie(location: String, cookie: Cookie): HttpResponseData =
        respond(
            "redirect",
            HttpStatusCode.TemporaryRedirect,
            headers = Headers.build {
                append(HttpHeaders.Location, location)
                append(HttpHeaders.SetCookie, renderSetCookieHeader(cookie))
            }
        )
}
