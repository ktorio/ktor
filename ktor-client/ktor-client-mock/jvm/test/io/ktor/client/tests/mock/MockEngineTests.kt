package io.ktor.client.tests.mock

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.serialization.Serializable
import kotlin.test.*

class MockEngineTests {
    @Test
    fun testClientMock() = runBlocking {
        val mockEngine = MockEngine {
            if (url.encodedPath == "/") MockHttpResponse(
                call,
                HttpStatusCode.OK,
                ByteReadChannel(byteArrayOf(1, 2, 3)),
                headersOf("X-MyHeader", "MyValue")
            ) else MockHttpResponse(
                call, HttpStatusCode.NotFound, ByteReadChannel("Not Found ${url.encodedPath}")
            )
        }

        val client = HttpClient(mockEngine) {
            expectSuccess = false
        }

        assertEquals(byteArrayOf(1, 2, 3).toList(), client.get<ByteArray>("/").toList())
        assertEquals("MyValue", client.call("/").response.headers["X-MyHeader"])
        assertEquals("Not Found other/path", client.get<String>("/other/path"))

        Unit
    }

    @Test
    fun testBasic() = testBlocking {
        val client = HttpClient(MockEngine {
            if (url.toString().endsWith("/fail")) {
                responseError(HttpStatusCode.BadRequest)
            } else {
                responseOk("$url")
            }
        })

        client.call { url("http://127.0.0.1/normal-request") }.apply {
            assertEquals("http://127.0.0.1/normal-request", response.readText())
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.call { url("http://127.0.0.1/fail") }.apply {
            assertEquals("Bad Request", response.readText())
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Serializable
    data class User(val name: String)

    @Test
    fun testWithJsonFeature() = runBlocking {
        val client = HttpClient(MockEngine {
            val bodyBytes = (content as OutgoingContent.ByteArrayContent).bytes()
            responseOk(String(bodyBytes))
        }) {
            install(JsonFeature)
        }

        val response = client.get<String>(body = User("admin")) {
            contentType(ContentType.Application.Json)
        }

        assertEquals("{\"name\":\"admin\"}", response)
    }

    private fun testBlocking(callback: suspend () -> Unit): Unit = run { runBlocking { callback() } }

}
