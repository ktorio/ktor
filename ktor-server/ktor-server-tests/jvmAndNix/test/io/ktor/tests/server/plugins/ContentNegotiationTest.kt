/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

@Suppress("DEPRECATION")
class ContentNegotiationTest {
    private val customContentType = ContentType.parse("application/ktor")

    private val customContentConverter = object : ContentConverter {
        override suspend fun serializeNullable(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is Wrapper) return null
            return TextContent("[${value.value}]", contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != Wrapper::class) return null
            return Wrapper(content.readRemaining().readText().removeSurrounding("[", "]"))
        }
    }

    private val textContentConverter = object : ContentConverter {
        override suspend fun serializeNullable(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            if (value !is Wrapper) return null
            return TextContent(value.value, contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != Wrapper::class) return null
            return Wrapper(content.readRemaining().readText())
        }
    }

    private fun alwaysFailingConverter(ignoreString: Boolean) = object : ContentConverter {
        override suspend fun serializeNullable(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
        ): OutgoingContent? {
            fail("This converter should be never started for send")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (ignoreString && typeInfo.type == String::class) return null
            fail("This converter should be never started for receive")
        }
    }

    @Test
    fun testEmpty(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
        }

        application.routing {
            get("/") {
                call.respond("OK")
            }
            post("/") {
                val text = call.receive<String>()
                call.respond("OK: $text")
            }
        }

        handleRequest(HttpMethod.Get, "/") { }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
            assertEquals("OK", call.response.content)
        }
        handleRequest(HttpMethod.Post, "/") {
            setBody("The Text")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
            assertEquals("OK: The Text", call.response.content)
        }
    }

    data class Wrapper(val value: String)

    @Test
    fun testTransformWithNotAcceptable(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Zip, customContentConverter)
        }

        application.routing {
            post("/") {
                call.respond(Wrapper("hello"))
            }
        }
        handleRequest(HttpMethod.Post, "/") {
            setBody(""" {"value" : "value" }""")
            addHeader(HttpHeaders.Accept, "application/xml")
            addHeader(HttpHeaders.ContentType, "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.NotAcceptable, call.response.status())
        }
    }

    @Test
    fun testTransformWithUnsupportedMediaType(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            register(ContentType.Application.Xml, customContentConverter)
        }

        application.routing {
            post("/") {
                val wrapper = call.receive<Wrapper>()
                call.respond(wrapper.value)
            }
        }
        handleRequest(HttpMethod.Post, "/") {
            setBody(""" {"value" : "value" }""")
            addHeader(HttpHeaders.Accept, "application/xml")
            addHeader(HttpHeaders.ContentType, "application/json")
        }.let { call ->
            assertEquals(HttpStatusCode.UnsupportedMediaType, call.response.status())
        }
    }

    @Test
    fun testCustom() {
        withTestApplication {
            application.install(ContentNegotiation) {
                register(customContentType, customContentConverter)
            }

            application.routing {
                get("/") {
                    call.respond(Wrapper("OK"))
                }
                post("/") {
                    val text = call.receive<Wrapper>().value
                    call.respond(Wrapper("OK: $text"))
                }
                post("/raw") {
                    val text = call.receiveText()
                    call.respond("RAW: $text")
                }
            }

            // Acceptable
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK]", call.response.content)
            }

            // Acceptable with charset
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
                addHeader(HttpHeaders.AcceptCharset, Charsets.ISO_8859_1.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.ISO_8859_1, call.response.contentType().charset())
                assertEquals("[OK]", call.response.content)
            }

            // Acceptable with any charset
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
                addHeader(HttpHeaders.AcceptCharset, "*, ISO-8859-1;q=0.5")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.UTF_8, call.response.contentType().charset())
                assertEquals("[OK]", call.response.content)
            }

            // Acceptable with multiple charsets and one preferred
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
                addHeader(HttpHeaders.AcceptCharset, "ISO-8859-1;q=0.5, UTF-8;q=0.8")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.UTF_8, call.response.contentType().charset())
                assertEquals("[OK]", call.response.content)
            }

            // Missing acceptable charset
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.UTF_8, call.response.contentType().charset()) // should be default
                assertEquals("[OK]", call.response.content)
            }

            // Unacceptable
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, ContentType.Text.Plain.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotAcceptable, call.response.status())
                assertNull(call.response.headers[HttpHeaders.ContentType])
                assertNull(call.response.content)
            }

            // Content-Type pattern
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, ContentType(customContentType.contentType, "*").toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.UTF_8, call.response.contentType().charset())
                assertEquals("[OK]", call.response.content)
            }

            // Content-Type twice
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, "$customContentType,$customContentType")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals(Charsets.UTF_8, call.response.contentType().charset())
                assertEquals("[OK]", call.response.content)
            }

            // Post
            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, customContentType.toString())
                addHeader(HttpHeaders.Accept, customContentType.toString())
                setBody("[The Text]")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK: The Text]", call.response.content)
            }

            // Post to raw endpoint with custom content type
            handleRequest(HttpMethod.Post, "/raw") {
                addHeader(HttpHeaders.ContentType, customContentType.toString())
                addHeader(HttpHeaders.Accept, customContentType.toString())
                setBody("[The Text]")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
                assertEquals("RAW: [The Text]", call.response.content)
            }

            // Post with charset
            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, customContentType.withCharset(Charsets.UTF_8).toString())
                addHeader(HttpHeaders.Accept, customContentType.toString())
                setBody("[The Text]")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK: The Text]", call.response.content)
            }
        }
    }

    @Test
    fun testSubrouteInstall() {
        withTestApplication {
            application.routing {
                application.routing {
                    route("1") {
                        install(ContentNegotiation) {
                            register(customContentType, customContentConverter)
                        }
                        get { call.respond(Wrapper("OK")) }
                    }
                    get("2") { call.respond(Wrapper("OK")) }
                }
            }

            handleRequest(HttpMethod.Get, "/1") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK]", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotAcceptable, call.response.status())
            }
        }
    }

    @Test
    fun testMultiple() {
        val textContentConverter: ContentConverter = textContentConverter

        withTestApplication {
            application.install(ContentNegotiation) {
                // Order here matters. The first registered content type matching the Accept header will be chosen.
                register(customContentType, customContentConverter)
                register(ContentType.Text.Plain, textContentConverter)
            }

            application.routing {
                get("/") {
                    call.respond(Wrapper("OK"))
                }
            }

            // Accept: application/ktor
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, customContentType.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK]", call.response.content)
            }

            // Accept: text/plain
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, ContentType.Text.Plain.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
                assertEquals("OK", call.response.content)
            }

            // Accept: text/*
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, ContentType.Text.Any.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
                assertEquals("OK", call.response.content)
            }

            // Accept: */*
            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Accept, ContentType.Any.toString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK]", call.response.content)
            }

            // No Accept header
            handleRequest(HttpMethod.Get, "/") {
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK]", call.response.content)
            }
        }
    }

    @Suppress("ReplaceSingleLineLet", "MoveLambdaOutsideParentheses")
    @Test
    fun testReceiveTransformedByDefault(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            // Order here matters. The first registered content type matching the Accept header will be chosen.
            register(ContentType.Any, alwaysFailingConverter(true))
            ignoreType<String>()
        }

        application.routing {
            post("/byte-channel") {
                val count = call.receive<ByteReadChannel>().discard()
                call.respondText("bytes: $count")
            }
            post("/byte-array") {
                val array = call.receive<ByteArray>()
                call.respondText("array: ${array.size}")
            }
            post("/string") {
                val text = call.receive<String>()
                call.respondText("text: $text")
            }
            post("/parameters") {
                val receivedParameters = call.receiveParameters()
                call.respondText(receivedParameters.toString())
            }
        }

        handleRequest(HttpMethod.Post, "/byte-channel", { setBody("123") }).let { call ->
            assertEquals("bytes: 3", call.response.content)
        }

        handleRequest(HttpMethod.Post, "/byte-array", { setBody("123") }).let { call ->
            assertEquals("array: 3", call.response.content)
        }

        handleRequest(HttpMethod.Post, "/string", { setBody("123") }).let { call ->
            assertEquals("text: 123", call.response.content)
        }

        handleRequest(HttpMethod.Post, "/parameters") {
            setBody("k=v")
            addHeader(
                HttpHeaders.ContentType,
                ContentType.Application.FormUrlEncoded.toString()
            )
        }.let { call ->
            assertEquals("Parameters [k=[v]]", call.response.content)
        }
    }

    @Test
    fun testReceiveTextIgnoresContentNegotiation(): Unit = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Any, alwaysFailingConverter(false))
        }

        routing {
            post("/text") {
                val text = call.receiveText()
                call.respondText("text: $text")
            }
        }

       client.post("/text") {
            setBody("\"k=v\"")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertEquals("text: \"k=v\"", response.bodyAsText())
        }
    }

    @Test
    fun testRespondByteReadChannelIgnoresContentNegotiation(): Unit = testApplication {
        install(ContentNegotiation) {
            register(ContentType.Any, alwaysFailingConverter(false))
        }

        routing {
            get("/text") {
                call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                call.respond(ByteReadChannel("""{"x": 123}""".toByteArray()))
            }
        }

        client.get("/text").let { response ->
            assertEquals("""{"x": 123}""", response.bodyAsText())
        }
    }

    @Test
    fun testCustomAcceptedContentTypesContributor(): Unit = withTestApplication {
        with(application) {
            install(ContentNegotiation) {
                register(ContentType.Text.Plain, textContentConverter)
                register(ContentType.Text.Html, textContentConverter)

                accept { call, acceptedContentTypes ->
                    call.request.queryParameters["format"]?.let { format ->
                        when (format) {
                            "text" -> listOf(ContentTypeWithQuality(ContentType.Text.Plain))
                            "html" -> listOf(ContentTypeWithQuality(ContentType.Text.Html))
                            else -> null
                        }
                    } ?: acceptedContentTypes
                }
            }

            routing {
                get("/") {
                    call.respond(Wrapper("test content"))
                }
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/plain")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/html")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Html, call.response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/plain, text/html")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "text/plain; q=0.9, text/html")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Html, call.response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Get, "/?format=html") {
            addHeader(HttpHeaders.Accept, "text/plain")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Html, call.response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Get, "/?format=text") {
            addHeader(HttpHeaders.Accept, "text/html")
        }.let { call ->
            assertEquals("test content", call.response.content)
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
        }
    }

    @Test
    fun testDoubleReceive(): Unit = withTestApplication {
        with(application) {
            install(DoubleReceive)
            install(ContentNegotiation) {
                register(ContentType.Text.Plain, textContentConverter)
            }
        }

        application.routing {
            get("/") {
                call.respondText(call.receive<Wrapper>().value + "-" + call.receive<Wrapper>().value)
            }
        }

        handleRequest(HttpMethod.Get, "/?format=text") {
            addHeader(HttpHeaders.Accept, "text/plain")
            addHeader(HttpHeaders.ContentType, "text/plain")
            setBody("[content]")
        }.let { call ->
            assertEquals("[content]-[content]", call.response.content)
            assertEquals(ContentType.Text.Plain, call.response.contentType().withoutParameters())
        }
    }

    @Test
    fun testIllegalAcceptAndContentTypes(): Unit = withTestApplication {
        with(application) {
            install(ContentNegotiation) {
                register(ContentType.Text.Plain, textContentConverter)
            }

            routing {
                get("/receive") {
                    assertFailsWith<BadRequestException> {
                        call.receive<String>()
                    }.let { throw it }
                }
                get("/send") {
                    assertFailsWith<BadRequestException> {
                        call.respond(Any())
                    }.let { throw it }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/receive") {
            addHeader("Content-Type", "...la..lla..la")
            setBody("any")
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "....aa..laa...laa")
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    fun testIllegalAcceptAndCheckAcceptHeader(): Unit = withTestApplication {
        with(application) {
            install(ContentNegotiation) {
                checkAcceptHeaderCompliance = true
                register(ContentType.Text.Plain, textContentConverter)
            }

            routing {
                get("/send") {
                    assertFailsWith<BadRequestException> {
                        call.respond(Any())
                    }.let { throw it }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "....aa..laa...laa")
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    fun testMatchingAcceptAndContentTypes(): Unit = withTestApplication {
        with(application) {
            install(ContentNegotiation) {
                checkAcceptHeaderCompliance = true
            }

            routing {
                get("/send") {
                    call.respond("some text")
                }
            }
        }

        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "text/plain")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "application/json, text/plain;q=0.1")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "*/*")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }

        handleRequest(HttpMethod.Get, "/send") {
            addHeader("Accept", "text/*")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
    }
}
