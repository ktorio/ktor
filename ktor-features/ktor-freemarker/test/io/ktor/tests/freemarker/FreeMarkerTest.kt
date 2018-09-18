package io.ktor.tests.freemarker

import freemarker.cache.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.util.zip.*
import kotlin.test.*

class FreeMarkerTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("e", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testEmptyModel() {
        withTestApplication {
            application.setUpTestTemplates()
            application.routing {
                get("/") {
                    call.respondTemplate("empty.ftl")
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, Anonymous</p>", "<h1>Hi!</h1>"))
                }
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertNull(response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testCompression() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(Compression)
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respondTemplate("test.ftl", model, "e")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                @Suppress("DEPRECATION_ERROR")
                assert(content.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("e", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testWithoutEtag() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals(null, response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun canRespondAppropriately() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate("test.ftl", model)
                }
            }

            val call = handleRequest(HttpMethod.Get, "/")

            with(call.response) {
                assertNotNull(content)

                val lines = content!!.lines()

                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            }
        }
    }

    private fun Application.setUpTestTemplates() {
        val bax = "$"

        install(FreeMarker) {
            templateLoader = StringTemplateLoader().apply {
                putTemplate("test.ftl", """
                        <p>Hello, $bax{id}</p>
                        <h1>$bax{title}</h1>
                    """.trimIndent())
                putTemplate("empty.ftl", """
                        <p>Hello, Anonymous</p>
                        <h1>Hi!</h1>
                    """.trimIndent())
            }
        }
    }
}
