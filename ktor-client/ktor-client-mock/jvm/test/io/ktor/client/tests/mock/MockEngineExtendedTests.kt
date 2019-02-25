package io.ktor.client.tests.mock

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class MockEngineExtendedTests {

    @Test
    fun testExecutionOrder() = runBlocking {
        val mockEngine = MockEngine.config {
            addHandler { respondOkText("first") }
            addHandler { respondBadRequest() }
            addHandler { respondOkText("third") }
        }

        val client = HttpClient(mockEngine) { expectSuccess = false }

        assertEquals("first", client.get())
        assertEquals("Bad Request", client.get())
        assertEquals("third", client.get())
    }

    @Test
    fun testReceivedRequest() = runBlocking {
        val mockEngine = MockEngine.config {
            addHandler { respondOkText("first") }
            addHandler { respondOkText("second") }
            addHandler { respondOkText("third") }
        }.create() as MockEngine

        val client = HttpClient(mockEngine)

        client.call("http://127.0.0.1") {
            header("header", "first")
            body = "body"
        }

        client.call("https://127.0.0.02") {
            header("header", "second")
            body = "secured"
        }

        val firstCall = mockEngine.requestHistory[0]
        val secondCall = mockEngine.requestHistory[1]

        assertEquals(firstCall.url.fullUrl, "http://127.0.0.1")
        assertEquals(firstCall.headers["header"], "first")
        assertEquals((firstCall.content as TextContent).text, "body")
        assertEquals(secondCall.url.fullUrl, "https://127.0.0.02")
        assertEquals(secondCall.headers["header"], "second")
        assertEquals((secondCall.content as TextContent).text, "secured")
    }

    @Test
    fun testUnhandledRequest() = runBlocking {
        val mockEngine = MockEngine.config {
            addHandler { respondOkText("text") }
            reuseHandlers = false
        }

        val client = HttpClient(mockEngine)

        runBlocking {
            client.get<String>("/")
        }

        val exception = assertFails {
            runBlocking {
                client.get<String>("/unhandled")
            }
        }

        assertEquals("Unhandled http://localhost/unhandled", exception.message)
    }

    private fun HttpClientCall.respondOkText(text: String) = MockHttpResponse(
        this,
        HttpStatusCode.OK,
        ByteReadChannel(text.toByteArray()),
        headersOf("header", "value")
    )

    private fun HttpClientCall.respondBadRequest() = MockHttpResponse(
        this,
        HttpStatusCode.BadRequest,
        ByteReadChannel("Bad Request".toByteArray())
    )

    private val Url.fullUrl: String get() = "${protocol.name}://$host"
}
