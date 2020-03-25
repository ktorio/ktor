/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class FullFormTest : ClientLoader() {
    @Test
    fun testGet() = clientTests {
        test { client ->
            val text = client.request<HttpStatement> {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = 8080
                    encodedPath = "/forms/hello"
                    method = HttpMethod.Get
                }
            }.execute { it.readText() }

            assertEquals("Hello, client", text)
        }
    }

    @Test
    fun testPost() = clientTests {
        test { client ->
            val text = client.request<HttpStatement> {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = 8080
                    encodedPath = "/forms/hello"
                    method = HttpMethod.Post
                    body = "Hello, server"
                }
            }.execute { it.readText() }

            assertEquals("Hello, client", text)
        }
    }

    @Test
    fun testRequest() = clientTests {
        test { client ->
            val requestBuilder = request {
                url {
                    host = "127.0.0.1"
                    protocol = URLProtocol.HTTP
                    port = 8080
                    encodedPath = "/forms/hello"
                    method = HttpMethod.Post
                    body = "Hello, server"
                }
            }

            val body = client.request<String>(requestBuilder)
            assertEquals("Hello, client", body)
        }
    }

    @Test
    fun testCustomUrls() = clientTests(listOf("iOS")) {
        val urls = listOf(
            "https://google.com",
            "https://kotlinlang.org/"
        )

        test { client ->
            urls.forEach {
                client.get<String>(it)
            }
        }
    }
}
