/*
 * Copyright 2014-2012 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.jte

import gg.jte.*
import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.server.application.*
import io.ktor.server.jte.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.zip.*
import kotlin.test.*

@Suppress("DEPRECATION")
class JteTest {

    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)
            application.routing {
                val params = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(JteContent("test.kte", params, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                val content = response.content!!.lines()
                assertEquals(expectedContent, content)
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testEmptyModel() {
        withTestApplication {
            application.setUpTestTemplates()
            application.routing {
                get("/") {
                    call.respondTemplate("empty.kte")
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                assertEquals(listOf("<p>Hello, Anonymous</p>", "<h1>Hi!</h1>"), response.content!!.lines())
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
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                val params = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respondTemplate("test.kte", params, "e")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                assertEquals(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"), content.lines())
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testWithoutEtag() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)

            application.routing {
                val params = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(JteContent("test.kte", params))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                assertEquals(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"), response.content!!.lines())
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
                get("/") {
                    call.respondTemplate("test.kte", "id" to 1, "title" to "Bonjour le monde!")
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

    @Test
    fun testErrorInContent() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(StatusPages) {
                exception<Throwable> { call, _ ->
                    call.respond("Error: template exception")
                }
            }
            application.install(ConditionalHeaders)
            application.routing {
                get("/") {
                    call.respond(JteContent("test.kte", emptyMap()))
                }
            }

            assertEquals("Error: template exception", handleRequest(HttpMethod.Get, "/").response.content)
        }
    }

    private fun Application.setUpTestTemplates() {
        val bax = "$"

        install(Jte) {
            val resolver = StringTemplateResolver().apply {
                putTemplate(
                    "test.kte",
                    """
                        @param id: Int
                        @param title: String
                        <p>Hello, $bax{id}</p>
                        <h1>$bax{title}</h1>
                    """.trimIndent()
                )
                putTemplate(
                    "empty.kte",
                    """
                        <p>Hello, Anonymous</p>
                        <h1>Hi!</h1>
                    """.trimIndent()
                )
            }
            templateEngine = TemplateEngine.create(resolver, gg.jte.ContentType.Html)

        }
    }

    private class StringTemplateResolver : CodeResolver {

        private val templates = mutableMapOf<String, String>()

        override fun resolve(name: String?): String {
            return templates[name!!]!!
        }

        override fun getLastModified(name: String?): Long {
            return 0
        }

        fun putTemplate(name: String, template: String) {
            templates[name] = template
        }
    }
}
