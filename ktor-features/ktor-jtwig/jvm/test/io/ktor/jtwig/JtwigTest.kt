/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.jtwig

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.util.zip.*
import kotlin.test.*

class JtwigTest {
    @Test
    fun `Fill template and expect correct rendered content`() {
        withTestApplication {
            application.setupJtwig()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(JtwigContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val lines = response.content!!.lines()
                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Hello World!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Fill template and expect correct default content type`() {
        withTestApplication {
            application.setupJtwig()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(JtwigContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
                assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
            }
        }
    }

    @Test
    fun `Fill template and expect eTag set when it is provided`() {
        withTestApplication {
            application.setupJtwig()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(JtwigContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            assertEquals("\"e\"", handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Render empty model`() {
        withTestApplication {
            application.setupJtwig()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(JtwigContent(TemplateWithoutPlaceholder, emptyMap(), "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val lines = response.content!!.lines()

                assertEquals("<p>Hello, Anonymous</p>", lines[0])
                assertEquals("<h1>Hi!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Render template compressed with GZIP`() {
        withTestApplication {
            application.setupJtwig()
            application.install(Compression)
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respondTemplate(TemplateWithPlaceholder, DefaultModel, "e")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                val lines = content.lines()

                assertEquals("<p>Hello, 1</p>", lines[0])
                assertEquals("<h1>Hello World!</h1>", lines[1])
            }
        }
    }

    @Test
    fun `Resolve template location with prefix and suffix configuration`() {
        withTestApplication {
            application.setupJtwig {
                templatePrefix = "classpath:templates/"
                templateSuffix = ".twig.html"
            }
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(JtwigContent("helloWorld"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                val lines = response.content!!.lines()
                assertEquals("<p>Hello, World!</p>", lines[0])
            }
        }
    }

    private fun Application.setupJtwig(configure: Jtwig.Configuration.() -> Unit = {}) {
        install(Jtwig, configure)
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")
        private const val TemplateWithPlaceholder = "withPlaceholder.twig.html"
        private const val TemplateWithoutPlaceholder = "withoutPlaceholder.twig.html"
    }
}
