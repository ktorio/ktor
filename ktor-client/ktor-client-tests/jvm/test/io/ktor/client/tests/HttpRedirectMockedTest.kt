/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class HttpRedirectMockedTest {
    @Test
    fun testRelativeRedirect() = testWithEngine(MockEngine) {
        config {
            server { "child" }
        }

        test { client ->
            client.get("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
            }

            client.get("http://localhost/path/").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/path/child", response.request.url.fullPath)
            }
        }
    }

    @Test
    fun testAbsoluteRedirect() = testWithEngine(MockEngine) {
        config {
            server { "/child" }
        }

        test { client ->
            client.get("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
            }

            client.get("http://localhost/path/").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
            }
        }
    }

    @Test
    fun testHostRedirect() = testWithEngine(MockEngine) {
        config {
            server { "http://localhost2/child" }
        }

        test { client ->
            client.get("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
            }
        }
    }

    @Test
    fun testHttpsRedirect() = testWithEngine(MockEngine) {
        config {
            server { "https://localhost2/child" }
        }

        test { client ->
            client.get("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("https", response.request.url.protocol.name)
            }
        }
    }

    @Test
    fun testHttpsRedirectFromHttps() = testWithEngine(MockEngine) {
        config {
            server { "https://localhost2/child" }
        }

        test { client ->
            client.get("https://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("https", response.request.url.protocol.name)
            }
        }
    }

    @Test
    fun testHttpRedirectFromHttps() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            server { "http://localhost2/child" }
        }

        test { client ->
            assertFailsWith<RedirectResponseException> {
                client.get("https://localhost/path").body<String>()
            }
        }
    }

    @Test
    fun testHttpRedirectFromHttpsDowngradeAllowed() = testWithEngine(MockEngine) {
        config {
            server { "http://localhost2/child" }
            install(HttpRedirect) {
                allowHttpsDowngrade = true
            }
        }

        test { client ->
            client.get("https://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("http", response.request.url.protocol.name)
            }
        }
    }

    @Test
    fun testAuthHeaderResend() = testWithEngine(MockEngine) {
        config {
            server {
                when (it.url.parameters["i"]) {
                    "1" -> "http://localhost/child"
                    "2" -> "http://localhost:80/child"
                    "3" -> "http://localhost:443/child"
                    "4" -> "https://localhost/child"
                    "5" -> "http://otherhost/child"
                    else -> fail()
                }
            }
        }

        test { client ->
            suspend fun run(url: String, block: suspend (HttpResponse) -> Unit) {
                block(client.get(url) { header(HttpHeaders.Authorization, "aab") })
            }

            val results = HashMap<String, String>()

            repeat(5) {
                val number = it + 1
                run("http://localhost/path?i=$number") { response ->
                    assertEquals("OK", response.bodyAsText())
                    results[number.toString()] = response.headers["_auth"]!!
                }
            }

            assertEquals("aab", results["1"])
            assertEquals("aab", results["2"])
            assertEquals("", results["3"])
            assertEquals("aab", results["4"])
            assertEquals("", results["5"])
        }
    }

    @Test
    fun testProhibitedRedirectHttpMethodCheck() = testWithEngine(MockEngine) {
        config {
            expectSuccess = true
            server {
                "http://localhost/child"
            }
        }

        test { client ->
            assertEquals("OK", client.get("http://localhost/path").bodyAsText())

            assertFailsWith<RedirectResponseException> {
                assertEquals("OK", client.post("http://localhost/path").bodyAsText())
            }
        }
    }

    @Test
    fun testAllowedRedirectHttpMethodCheck() = testWithEngine(MockEngine) {
        config {
            server {
                "http://localhost/child"
            }
            install(HttpRedirect) {
                checkHttpMethod = false
            }
        }

        test { client ->
            client.get("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
            }

            client.post("http://localhost/path").let { response ->
                assertEquals("OK", response.bodyAsText())
            }
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.server(block: (HttpRequestData) -> String) {
        engine {
            addHandler { request ->
                if (request.url.fullPath.endsWith("child")) {
                    respond(
                        "OK",
                        HttpStatusCode.OK,
                        headers = Headers.build {
                            append("_auth", request.headers[HttpHeaders.Authorization] ?: "")
                        }
                    )
                } else {
                    respond(
                        "redirect",
                        HttpStatusCode.PermanentRedirect,
                        headers = Headers.build {
                            append(HttpHeaders.Location, block(request))
                            append("_auth", request.headers[HttpHeaders.Authorization] ?: "")
                        }
                    )
                }
            }
        }
    }
}
