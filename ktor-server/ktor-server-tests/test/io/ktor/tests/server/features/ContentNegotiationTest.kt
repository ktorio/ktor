package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.io.*
import org.junit.Test
import kotlin.test.*

class ContentNegotiationTest {
    private val customContentType = ContentType.parse("application/ktor")

    private val customContentConverter = object : ContentConverter {
        override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
            if (value !is Wrapper) return null
            return TextContent("[${value.value}]", contentType.withCharset(context.call.suitableCharset()))
        }

        override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
            val type = context.subject.type
            val channel = context.subject.value
            if (type != Wrapper::class || channel !is ByteReadChannel) return null
            return Wrapper(channel.readRemaining().readText().removeSurrounding("[", "]"))
        }
    }

    private val textContentConverter =  object : ContentConverter {
        override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
            if (value !is Wrapper) return null
            return TextContent(value.value, contentType.withCharset(context.call.suitableCharset()))
        }

        override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
            val type = context.subject.type
            val incoming = context.subject.value
            if (type != Wrapper::class || incoming !is ByteReadChannel) return null
            return Wrapper(incoming.readRemaining().readText())
        }
    }

    @Test
    fun testEmpty() = withTestApplication {
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

        handleRequest(HttpMethod.Get, "/") {

        }.let { call ->
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
                addHeader(HttpHeaders.Accept, ContentType(customContentType.contentType, "*").toString() )
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
}