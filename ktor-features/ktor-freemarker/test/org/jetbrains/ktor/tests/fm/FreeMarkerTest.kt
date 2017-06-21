package org.jetbrains.ktor.tests.fm

import freemarker.cache.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import java.util.zip.*
import kotlin.test.*

class FreeMarkerTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION")
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
    fun testCompression() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(Compression)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                @Suppress("DEPRECATION")
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

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals(null, response.headers[HttpHeaders.ETag])
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
            }
        }
    }
}
