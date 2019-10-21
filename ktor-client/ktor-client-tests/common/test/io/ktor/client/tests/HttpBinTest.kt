/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@Serializable
data class HttpBinResponse(
    val url: String,
    val args: Map<String, String>,
    val headers: Map<String, String>
)

class HttpBinTest : ClientLoader() {

    @Test
    fun testGet() = clientTests {
        config {
            testConfiguration()
        }

        test { client ->
            val response = client.get<HttpBinResponse>("https://httpbin.org/get")

            assertEquals("https://httpbin.org/get", response.url)
            assertEquals(emptyMap(), response.args)

            with(response.headers) {
                assertEquals("application/json", get("Accept"))
                assertEquals("httpbin.org", get("Host"))
            }

        }
    }

    @Test
    fun testPost() = clientTests {
        config {
            testConfiguration()
        }

        test { client ->
            val response = client.post<HttpBinResponse>("https://httpbin.org/post") {
                body = "Hello, bin!"
            }

            assertEquals("https://httpbin.org/post", response.url)
            assertEquals(emptyMap(), response.args)

            with(response.headers) {
                assertEquals("text/plain; charset=UTF-8", get("Content-Type"))
                assertEquals("application/json", get("Accept"))
                assertEquals("11", get("Content-Length"))
                assertEquals("httpbin.org", get("Host"))
            }
        }
    }

    @Test
    fun testBytes() = clientTests {
        test { client ->
            val size = 100 * 1024
            val response = client.get<HttpStatement>("https://httpbin.org/bytes/$size").execute {
                it.readBytes()
            }
            assertEquals(size, response.size)
        }
    }

    private fun HttpClientConfig<*>.testConfiguration() {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict)
        }
    }
}
