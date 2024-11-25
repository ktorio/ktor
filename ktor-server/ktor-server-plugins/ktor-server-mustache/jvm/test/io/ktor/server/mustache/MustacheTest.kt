/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.mustache

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import java.util.zip.*
import kotlin.test.*
import kotlin.text.Charsets

class MustacheTest {

    @Test
    fun `Fill template and expect correct rendered content`() = testApplication {
        setupMustache()
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
            }
        }

        client.get("/").let { response ->
            val lines = response.bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Hello World!</h1>", lines[1])
        }
    }

    @Test
    fun `Fill template and expect correct default content type`() = testApplication {
        setupMustache()
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
            }
        }

        client.get("/").let { response ->

            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun `Fill template and expect eTag set when it is provided`() = testApplication {
        setupMustache()
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
            }
        }

        assertEquals("\"e\"", client.get("/").headers[HttpHeaders.ETag])
    }

    @Test
    fun `Render empty model`() = testApplication {
        setupMustache()
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respond(MustacheContent(TemplateWithoutPlaceholder, null, "e"))
            }
        }

        client.get("/").let { response ->

            val lines = response.bodyAsText().lines()

            assertEquals("<p>Hello, Anonymous</p>", lines[0])
            assertEquals("<h1>Hi!</h1>", lines[1])
        }
    }

    @Test
    fun `Render template compressed with GZIP`() = testApplication {
        setupMustache()
        install(Compression) {
            gzip { minimumSize(10) }
        }
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respondTemplate(TemplateWithPlaceholder, DefaultModel, "e")
            }
        }

        client.get("/") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { response ->
            val content = GZIPInputStream(response.bodyAsChannel().toInputStream()).reader().readText()

            val lines = content.lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Hello World!</h1>", lines[1])
        }
    }

    @Test
    fun `Render template without eTag`() = testApplication {
        setupMustache()
        install(ConditionalHeaders)

        routing {
            get("/") {
                call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel))
            }
        }

        assertEquals(null, client.get("/").headers[HttpHeaders.ETag])
    }

    @Test
    fun `Content Negotiation invoked after`() = testApplication {
        install(ContentNegotiation) {
            val alwaysFailingConverter = object : ContentConverter {
                override suspend fun serialize(
                    contentType: ContentType,
                    charset: Charset,
                    typeInfo: TypeInfo,
                    value: Any?
                ): OutgoingContent? {
                    fail("This converter should be never started for send")
                }

                override suspend fun deserialize(
                    charset: Charset,
                    typeInfo: TypeInfo,
                    content: ByteReadChannel
                ): Any? {
                    fail("This converter should be never started for receive")
                }
            }
            register(ContentType.Application.Json, alwaysFailingConverter)
        }
        setupMustache()

        routing {
            get("/") {
                call.respond(MustacheContent("index.hbs", mapOf<String, String>()))
            }
        }

        val response = client.get("/")
        assertEquals("Template", response.bodyAsText().trim())
    }

    private fun ApplicationTestBuilder.setupMustache() {
        install(Mustache)
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")

        private const val TemplateWithPlaceholder = "withPlaceholder.mustache"
        private const val TemplateWithoutPlaceholder = "withoutPlaceholder.mustache"
    }
}
