/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.test.*

@Suppress("PublicApiImplicitType")
class HttpRedirectTest : ClientLoader() {
    private val TEST_URL_BASE = "$TEST_SERVER/redirect"

    @Test
    fun testRedirect() = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            client.prepareGet(TEST_URL_BASE).execute {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.bodyAsText())
            }
        }
    }

    @Test
    fun testRedirectWithEncodedUri() = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            client.prepareGet("$TEST_URL_BASE/encodedQuery").execute {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("/redirect/getWithUri?key=value1%3Bvalue2%3D%22some=thing", it.bodyAsText())
            }
        }
    }

    @Test
    fun testInfinityRedirect() = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            assertFails {
                client.get("$TEST_URL_BASE/infinity")
            }
        }
    }

    @Test
    fun testRedirectWithCookies() = clientTests(except("Js")) {
        config {
            install(HttpCookies)
            install(HttpRedirect)
        }

        test { client ->
            client.prepareGet("$TEST_URL_BASE/cookie").execute {
                assertEquals("OK", it.bodyAsText())
                val token = client.plugin(HttpCookies).get(it.call.request.url)["Token"]!!
                assertEquals("Hello", token.value)
            }
        }
    }

    @Test
    @Ignore
    fun testCustomUrls() = clientTests(except("Darwin", "DarwinLegacy")) {
        val urls = listOf(
            "https://files.forgecdn.net/files/2574/880/BiblioCraft[v2.4.5][MC1.12.2].jar",
            "https://files.forgecdn.net/files/2611/560/Botania r1.10-356.jar",
            "https://files.forgecdn.net/files/2613/730/Toast Control-1.12.2-1.7.1.jar"
        )

        config {
            install(HttpRedirect)
        }

        test { client ->
            urls.forEach { url ->
                client.prepareGet(url).execute {
                    if (it.status.value >= 500) return@execute
                    assertTrue(it.status.isSuccess(), url)
                }
            }
        }
    }

    @Test
    fun testRedirectRelative() = clientTests {
        test { client ->
            client.prepareGet("$TEST_URL_BASE/directory/redirectFile").execute {
                assertEquals("targetFile", it.bodyAsText())
            }
        }
    }

    @Test
    fun testMultipleRedirectRelative() = clientTests {
        test { client ->
            client.prepareGet("$TEST_URL_BASE/multipleRedirects/login").execute {
                assertEquals("account details", it.bodyAsText())
            }
        }
    }

    @Test
    fun testRedirectAbsolute() = clientTests {
        test { client ->
            client.prepareGet("$TEST_URL_BASE/directory/absoluteRedirectFile").execute {
                assertEquals("absoluteTargetFile", it.bodyAsText())
            }
        }
    }

    @Test
    fun testRedirectHostAbsolute() = clientTests(except("Js")) {
        test { client ->
            client.prepareGet("$TEST_URL_BASE/directory/hostAbsoluteRedirect").execute {
                assertEquals("OK", it.bodyAsText())
                assertEquals("$TEST_URL_BASE/get", it.call.request.url.toString())
            }
        }
    }

    @Test
    fun testRedirectDisabled() = clientTests {
        config {
            followRedirects = false
        }

        test { client ->
            if (PlatformUtils.IS_BROWSER) return@test

            val response = client.get(TEST_URL_BASE)
            assertEquals(HttpStatusCode.Found, response.status)
        }
    }
}
