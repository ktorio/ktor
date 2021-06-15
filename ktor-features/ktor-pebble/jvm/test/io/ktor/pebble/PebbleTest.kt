/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.pebble

import com.mitchellbosecke.pebble.loader.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import java.util.zip.*
import kotlin.test.*

class PebbleTest {

    @Test
    fun `Fill template and expect correct rendered content`() {
        withTestApplication {
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
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
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
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
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel, etag = "e"))
                }
            }

            assertEquals("\"e\"", handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    @Test
    fun `Render empty model`() {
        withTestApplication {
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithoutPlaceholder, emptyMap(), etag = "e"))
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
            application.setupPebble()
            application.install(Compression) {
                gzip { minimumSize(10) }
            }
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respondTemplate(TemplateWithPlaceholder, DefaultModel, etag = "e")
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
    fun `Render template without eTag`() {
        withTestApplication {
            application.setupPebble()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(PebbleContent(TemplateWithPlaceholder, DefaultModel))
                }
            }

            assertEquals(null, handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    private fun Application.setupPebble() {
        install(Pebble) {
            loader(StringLoader())
        }
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")

        private val TemplateWithPlaceholder
            get() = """
                <p>Hello, {{id}}</p>
                <h1>{{title}}</h1>
            """.trimIndent()
        private val TemplateWithoutPlaceholder: String
            get() = """
                <p>Hello, Anonymous</p>
                <h1>Hi!</h1>
            """.trimIndent()
    }
}
