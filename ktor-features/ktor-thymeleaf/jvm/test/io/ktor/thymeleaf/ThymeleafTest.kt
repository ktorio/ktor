/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.thymeleaf

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.thymeleaf.templateresolver.*
import java.util.zip.*
import kotlin.test.*

class ThymeleafTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpThymeleafStringTemplate()
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(STRING_TEMPLATE, model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, response.content!!.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testCompression() {
        withTestApplication {
            application.setUpThymeleafStringTemplate()
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(STRING_TEMPLATE, model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, content.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals("\"e\"", response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun testWithoutEtag() {
        withTestApplication {
            application.setUpThymeleafStringTemplate()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(STRING_TEMPLATE, model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                val expectedContent = listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>")
                assertEquals(expectedContent, response.content!!.lines())

                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
                assertEquals(null, response.headers[HttpHeaders.ETag])
            }
        }
    }

    @Test
    fun canRespondAppropriately() {
        withTestApplication {
            application.setUpThymeleafStringTemplate()
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate(STRING_TEMPLATE, model)
                }
            }

            val call = handleRequest(HttpMethod.Get, "/")

            with(call.response) {
                assertNotNull(content)

                val lines = content!!.lines()

                assertEquals(lines[0], "<p>Hello, 1</p>")
                assertEquals(lines[1], "<h1>Bonjour le monde!</h1>")
            }
        }
    }

    @Test
    fun testClassLoaderTemplateResolver() {
        withTestApplication {
            application.install(Thymeleaf) {
                val resolver = ClassLoaderTemplateResolver()
                resolver.setTemplateMode("HTML")
                resolver.prefix = "templates/"
                resolver.suffix = ".html"
                setTemplateResolver(resolver)
            }
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")
                get("/") {
                    call.respondTemplate("test", model)
                }
            }
            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val lines = response.content!!.lines()
                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Hello, World!</h1>", lines[1])
            }
        }
    }

    private fun Application.setUpThymeleafStringTemplate() {
        install(Thymeleaf) {
            setTemplateResolver(StringTemplateResolver())
        }
    }

    companion object {
        val bax = "$"
        private val STRING_TEMPLATE = """
            <p>Hello, [[$bax{id}]]</p>
            <h1 th:text="$bax{title}"></h1>
        """.trimIndent()
    }
}
