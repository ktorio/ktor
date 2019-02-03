package io.ktor.client.tests.mock

import io.ktor.client.*
import io.ktor.client.call.*
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
        val mockEngine = MockEngineExtended.create().apply {
            enqueueResponses(
                { respondOkText("first") },
                { respondBadRequest() },
                { respondOkText("third") })
        }

        val client = HttpClient(mockEngine) { expectSuccess = false }


        assertEquals("first", client.get())
        assertEquals("Bad Request", client.get())
        assertEquals("third", client.get())
    }

    @Test
    fun testReceivedRequest() = runBlocking {
        val mockEngine = MockEngineExtended.create().apply {
            enqueueResponses(
                { respondOkText("first") },
                { respondOkText("second") },
                { respondOkText("third") })
        }

        val client = HttpClient(mockEngine)

        client.call("http://127.0.0.1") {
            header("header", "first")
            body = "body"
        }

        client.call("https://127.0.0.02") {
            header("header", "second")
            body = "secured"
        }

        val firstCall = mockEngine.takeRequest(0)
        val secondCall = mockEngine.takeRequest()

        assertEquals(firstCall.url.fullUrl, "http://127.0.0.1")
        assertEquals(firstCall.headers["header"], "first")
        assertEquals((firstCall.content as TextContent).text, "body")
        assertEquals(secondCall.url.fullUrl, "https://127.0.0.02")
        assertEquals(secondCall.headers["header"], "second")
        assertEquals((secondCall.content as TextContent).text, "secured")
    }

    @Test
    fun testUnhandledRequest() = runBlocking {
        val mockEngine = MockEngineExtended.create().apply {
            enqueueResponses({ respondOkText("text") })
        }

        val client = HttpClient(mockEngine)

        val exception = assertFails {
            runBlocking {
                client.get<String>("/")
                client.get<String>("/unhandled")
            }
        }

        assertEquals(exception.message, "Unhandled http://localhost/unhandled on invocationCount=1")
    }

    @Test
    fun testReset() = runBlocking {
        val mockEngine = MockEngineExtended.create()
        val client = HttpClient(mockEngine)

        mockEngine.enqueueResponses(
            { respondOkText("first") },
            { respondOkText("second") }
        )
        assertEquals("first", client.get("/first"))
        assertEquals("first", mockEngine.takeRequest(0).url.encodedPath, "/first")

        mockEngine.reset()

        mockEngine.enqueueResponses({ respondOkText("third") })
        assertEquals("third", client.get("/third"))
        assertEquals("third", mockEngine.takeRequest(0).url.encodedPath, "/third")
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
