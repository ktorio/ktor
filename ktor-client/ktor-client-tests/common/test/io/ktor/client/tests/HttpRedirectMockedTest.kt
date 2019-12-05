/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class HttpRedirectMockedTest {
    @Test
    fun relativeRedirect(): Unit = clientTest(MockEngine) {
        config {
            server { "child" }
        }

        test { client ->
            client.get<HttpResponse>("http://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
            }

            client.get<HttpResponse>("http://localhost/path/").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/path/child", response.request.url.fullPath)
            }
        }
    }

    @Test
    fun absoluteRedirect(): Unit = clientTest(MockEngine) {
        config {
            server { "/child" }
        }

        test { client ->
            client.get<HttpResponse>("http://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
            }

            client.get<HttpResponse>("http://localhost/path/").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
            }
        }
    }

    @Test
    fun hostRedirect(): Unit = clientTest(MockEngine) {
        config {
            server { "http://localhost2/child" }
        }

        test { client ->
            client.get<HttpResponse>("http://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
            }
        }
    }

    @Test
    fun httpsRedirect(): Unit = clientTest(MockEngine) {
        config {
            server { "https://localhost2/child" }
        }

        test { client ->
            client.get<HttpResponse>("http://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("https", response.request.url.protocol.name)
            }
        }
    }

    @Test
    fun httpsRedirectFromHttps(): Unit = clientTest(MockEngine) {
        config {
            server { "https://localhost2/child" }
        }

        test { client ->
            client.get<HttpResponse>("https://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("https", response.request.url.protocol.name)
            }
        }
    }

    @Test
    fun httpRedirectFromHttps(): Unit = clientTest(MockEngine) {
        config {
            server { "http://localhost2/child" }
        }

        test { client ->
            assertFailsWith<RedirectResponseException> {
                client.get<HttpResponse>("https://localhost/path").let { response ->
                    assertEquals("OK", response.readText())
                }
            }
        }
    }

    @Test
    fun httpRedirectFromHttpsDowngradeAllowed(): Unit = clientTest(MockEngine) {
        config {
            server { "http://localhost2/child" }
            install(HttpRedirect) {
                allowHttpsDowngrade = true
            }
        }

        test { client ->
            client.get<HttpResponse>("https://localhost/path").let { response ->
                assertEquals("OK", response.readText())
                assertEquals("/child", response.request.url.fullPath)
                assertEquals("localhost2", response.request.url.host)
                assertEquals("http", response.request.url.protocol.name)
            }
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.server(block: (HttpRequestData) -> String) {
        engine {
            addHandler { request ->
                if (request.url.fullPath.endsWith("child")) {
                    respondOk("OK")
                } else {
                    respond("redirect", HttpStatusCode.PermanentRedirect, headers = Headers.build {
                        append(HttpHeaders.Location, block(request))
                    })
                }
            }
        }
    }
}
