/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class HttpRedirectTest : ClientLoader() {
    private val TEST_URL_BASE = "$TEST_SERVER/redirect"

    @Test
    fun testRedirect() = clientTests {
        config {
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpStatement>("$TEST_URL_BASE/").execute {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.readText())
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
                client.get<HttpResponse>("$TEST_URL_BASE/infinity")
            }
        }
    }

    @Test
    fun testRedirectWithCookies() = clientTests(listOf("Js")) {
        config {
            install(HttpCookies)
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpStatement>("$TEST_URL_BASE/cookie").execute {
                assertEquals("OK", it.readText())
                val token = client.feature(HttpCookies)!!.get(it.call.request.url)["Token"]!!
                assertEquals("Hello", token.value)
            }
        }
    }

    @Test
    fun testCustomUrls() = clientTests {
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
                client.get<HttpStatement>(url).execute {
                    if (it.status.value >= 500) return@execute
                    assertTrue(it.status.isSuccess(), url)
                }
            }
        }
    }

    @Test
    fun testRedirectRelative() = clientTests {
        test { client ->
            client.get<HttpStatement>("$TEST_URL_BASE/directory/redirectFile").execute {
                assertEquals("targetFile", it.readText())
            }
        }
    }

    @Test
    fun testRedirectAbsolute() = clientTests {
        test { client ->
            client.get<HttpStatement>("$TEST_URL_BASE/directory/absoluteRedirectFile").execute {
                assertEquals("absoluteTargetFile", it.readText())
            }
        }
    }

    @Test
    fun testRedirectHostAbsolute() = clientTests(listOf("Js")) {
        test { client ->
            client.get<HttpStatement>("$TEST_URL_BASE/directory/hostAbsoluteRedirect").execute {
                assertEquals("200 OK", it.readText())
                assertEquals("https://httpstat.us/200", it.call.request.url.toString())
            }
        }
    }
}
