package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.compat.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import kotlinx.io.core.*
import org.junit.Assert.*
import java.io.*
import kotlin.test.*

open class ContentTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val testSize = listOf(
        0, 1, // small edge cases
        4 * 1024 - 1, 4 * 1024, 4 * 1024 + 1, // ByteChannel edge cases
        16 * 1024 * 1024 // big
    )

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                val headers = call.request.headers
                call.respond(content)
            }
        }
    }

    @Test
    fun byteArrayTest(): Unit = testSize.forEach { size ->
        val content = makeArray(size)
        val response = requestWithBody<ByteArray>(content)

        assertArrayEquals("Test fail with size: $size", content, response)
    }

    @Test
    fun byteReadChannelTest(): Unit = testSize.forEach { size ->
        val content = makeArray(size)

        runBlocking {
            HttpClient(factory).use { client ->
                val response = client.post<HttpResponse>(path = "echo", port = serverPort, body = content)
                val responseData = response.content.toByteArray()

                assertArrayEquals("Test fail with size: $size", content, responseData)
            }
        }
    }

    @Test
    fun inputStreamTest(): Unit = testSize.forEach { size ->
        val content = makeArray(size)

        runBlocking {
            HttpClient(factory).use { client ->
                val response = client.post<InputStream>(path = "echo", port = serverPort, body = content)
                val responseData = response.readBytes()

                assertArrayEquals("Test fail with size: $size", content, responseData)
            }
        }
    }

    @Test
    fun stringTest() = testSize.forEach { size ->
        val content = makeString(size)
        val requestWithBody = requestWithBody<String>(content)
        assertArrayEquals("Test fail with size: $size", content.toByteArray(), requestWithBody.toByteArray())
    }

    @Test
    fun textContentTest() = testSize.forEach { size ->
        val content = makeString(size)
        val response = requestWithBody<String>(TextContent(content, ContentType.Text.Plain))

        assertArrayEquals("Test fail with size: $size", content.toByteArray(), response.toByteArray())
    }

    @Test
    fun byteArrayContent() = testSize.forEach { size ->
        val content = makeArray(size)
        val response = requestWithBody<ByteArray>(ByteArrayContent(content))

        assertArrayEquals("Test fail with size: $size", content, response)
    }

    @Test
    @Ignore
    fun localFileContentTest() {
        val response = requestWithBody<ByteArray>(LocalFileContent(File("build.gradle")))
        assertArrayEquals(File("build.gradle").readBytes(), response)
    }

    private inline fun <reified Response : Any> requestWithBody(body: Any): Response = runBlocking {
        HttpClient(factory).use { client ->
            client.post<Response>(path = "echo", port = serverPort) {
                this.body = body
            }
        }
    }
}
