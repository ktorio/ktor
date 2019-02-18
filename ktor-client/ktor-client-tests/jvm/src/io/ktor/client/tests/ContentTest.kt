package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.util.cio.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.*
import kotlinx.io.core.*
import org.junit.Assert.*
import java.io.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
abstract class ContentTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val testSize = listOf(
        0, 1, // small edge cases
        4 * 1024 - 1, 4 * 1024, 4 * 1024 + 1, // ByteChannel edge cases
        16 * 1024 * 1024 // big
    )

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                call.respond(content)
            }
            get("/news") {
                val form = call.request.queryParameters

                assertEquals("myuser", form["user"]!!)
                assertEquals("10", form["page"]!!)

                call.respond("100")
            }
            post("/sign") {
                val form = call.receiveParameters()

                assertEquals("myuser", form["user"]!!)
                assertEquals("abcdefg", form["token"]!!)

                call.respond("success")
            }
            post("/upload") {
                val parts = call.receiveMultipart().readAllParts()
                parts.forEach { part ->
                    assertEquals(part.contentDisposition?.disposition, "form-data")
                }
                call.respondText(parts.makeString())
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

    @Test
    fun getFormDataTest() = clientTest(factory) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "page" to listOf("10")
            )

            val response = client.submitForm<String>(
                path = "news", port = serverPort, encodeInQuery = true, formData = form
            )

            assertEquals("100", response)
        }
    }

    @Test
    fun postFormDataTest() = clientTest(factory) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "token" to listOf("abcdefg")
            )

            val response = client.submitForm<String>(
                path = "sign", port = serverPort, formData = form
            )

            assertEquals("success", response)
        }
    }

    @Test
    fun multipartFormDataTest() = clientTest(factory) {
        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeStringUtf8("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("file2", "urlencoded_name2.jpg", ContentType.Application.OctetStream) {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        test { client ->
            val response = client.submitFormWithBinaryData<String>(path = "upload", port = serverPort, formData = data())
            val contentString = data().makeString()
            assertEquals(contentString, response)
        }
    }

    private inline fun <reified Response : Any> requestWithBody(body: Any): Response = runBlocking {
        HttpClient(factory).use { client ->
            client.post<Response>(path = "echo", port = serverPort) {
                this.body = body
            }
        }
    }

    private fun filenameContentTypeAndContentString(provider: () -> Input, headers: Headers): String {
        val dispHeader: String = headers.getAll(HttpHeaders.ContentDisposition)!!.joinToString(";")
        val disposition: ContentDisposition = ContentDisposition.parse(dispHeader)
        val filename: String = disposition.parameter("filename") ?: ""
        val contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ""
        val content: String = provider().readText(Charsets.ISO_8859_1)
        return "$filename$contentType$content"
    }

    private fun List<PartData>.makeString(): String = buildString {
        val list = this@makeString
        list.forEach {
            appendln(it.name!!)
            val content = when (it) {
                is PartData.FileItem -> filenameContentTypeAndContentString(it.provider, it.headers)
                is PartData.FormItem -> it.value
                is PartData.BinaryItem -> filenameContentTypeAndContentString(it.provider, it.headers)
            }

            appendln(content)
        }
    }
}
