/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.mock

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.response.readText
import io.ktor.client.statement.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlin.test.*

class MockEngineTests {
    @Test
    fun testClientMock() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.encodedPath == "/") return@addHandler respond(
                        byteArrayOf(1, 2, 3),
                        headers = headersOf("X-MyHeader", "My Value")
                    )

                    return@addHandler respondError(HttpStatusCode.NotFound, "Not Found ${request.url.encodedPath}")
                }
            }
            expectSuccess = false
        }

        assertEquals(byteArrayOf(1, 2, 3).toList(), client.get("/").body<ByteArray>().toList())
        assertEquals("My Value", client.request("/").headers["X-MyHeader"])
        assertEquals("Not Found other/path", client.get("/other/path").body())

        Unit
    }

    @Test
    fun testBasic() = testBlocking {
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.toString().endsWith("/fail")) {
                    respondBadRequest()
                } else {
                    respondOk("${request.url}")
                }
            }
        ) {
            expectSuccess = false
        }

        client.prepareRequest {
            url("http://127.0.0.1/normal-request")
        }.execute { response ->
            assertEquals("http://127.0.0.1/normal-request", response.readText())
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.prepareRequest {
            url("http://127.0.0.1/fail")
        }.execute { response ->
            assertEquals("Bad Request", response.readText())
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Serializable
    data class User(val name: String)

    @Test
    fun testWithJsonFeature() = runBlocking {
        val client = HttpClient(
            MockEngine { request ->
                val bodyBytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
                respondOk(String(bodyBytes))
            }
        ) {
            install(JsonFeature)
        }

        val response = client.get {
            setBody(User("admin"))
            contentType(ContentType.Application.Json)
        }.body<String>()

        assertEquals("{\"name\":\"admin\"}", response)
    }

    private fun testBlocking(callback: suspend () -> Unit): Unit = run { runBlocking { callback() } }
}
