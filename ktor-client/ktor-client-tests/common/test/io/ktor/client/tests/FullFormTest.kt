/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class FullFormTest : ClientLoader() {
    @Test
    fun testGet() = clientTests {
        test { client ->
            val text = client.prepareRequest {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = 8080
                    encodedPath = "/forms/hello"
                    method = HttpMethod.Get
                }
            }.execute { it.bodyAsText() }

            assertEquals("Hello, client", text)
        }
    }

    @Test
    fun testPost() = clientTests {
        test { client ->
            val text = client.prepareRequest {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = 8080
                    encodedPath = "/forms/hello"
                }
                method = HttpMethod.Post
                setBody("Hello, server")
            }.execute { it.bodyAsText() }

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
                    setBody("Hello, server")
                }
            }

            val body = client.request(requestBuilder).body<String>()
            assertEquals("Hello, client", body)
        }
    }

    @Test
    @Ignore
    fun testCustomUrls() = clientTests(except("Darwin", "native:CIO", "DarwinLegacy")) {
        val urls = listOf(
            "https://google.com",
            "https://kotlinlang.org/"
        )

        test { client ->
            urls.forEach {
                client.get(it).body<String>()
            }
        }
    }
}
