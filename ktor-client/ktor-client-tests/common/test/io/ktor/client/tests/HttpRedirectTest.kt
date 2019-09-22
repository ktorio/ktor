/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class HttpRedirectTest : ClientLoader() {
    private val TEST_URL_BASE = "$TEST_SERVER/redirect"

    @Test
    fun redirectTest(): Unit = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpResponse>("$TEST_URL_BASE/").use {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.readText())
            }
        }
    }

    @Test
    fun infinityRedirectTest() = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            assertFails {
                client.get<HttpResponse>("$TEST_URL_BASE/infinity")
            }
        }
    }

    @Test
    fun redirectWithCookiesTest() = clientTests(listOf("js")) {
        config {
            install(HttpCookies)
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpResponse>("$TEST_URL_BASE/cookie").use {
                assertEquals("OK", it.readText())
                val token = client.feature(HttpCookies)!!.get(it.call.request.url)["Token"]!!
                assertEquals("Hello", token.value)
            }
        }
    }

    @Test
    fun customUrlsTest() = clientTests {
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
                client.get<HttpResponse>(url).use {
                    if (it.status.value >= 500) return@use
                    assertTrue(it.status.isSuccess(), url)
                }
            }
        }
    }

    @Test
    fun httpStatsTest() = clientTests {
        test { client ->
            client.get<HttpResponse>("https://httpstat.us/301").use { response ->
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }

    @Test
    fun redirectRelative() = clientTests {
        test { client ->
            client.get<HttpResponse>("$TEST_URL_BASE/directory/redirectFile").use {
                assertEquals("targetFile", it.readText())
            }
        }
    }

    @Test
    fun redirectAbsolute() = clientTests {
        test { client ->
            client.get<HttpResponse>("$TEST_URL_BASE/directory/absoluteRedirectFile").use {
                assertEquals("absoluteTargetFile", it.readText())
            }
        }
    }

    @Test
    fun redirectHostAbsolute() = clientTests(listOf("js")) {
        test { client ->
            client.get<HttpResponse>("$TEST_URL_BASE/directory/hostAbsoluteRedirect").use {
                assertEquals("200 OK", it.readText())
                assertEquals("https://httpstat.us/200", it.call.request.url.toString())
            }
        }
    }
}
