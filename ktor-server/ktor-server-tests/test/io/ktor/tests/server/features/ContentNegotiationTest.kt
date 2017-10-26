package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.*
import kotlin.test.*

class ContentNegotiationTest {
    private val customContentType = ContentType.parse("application/ktor")

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
            body = "The Text"
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
                register(customContentType, object : ContentConverter {
                    suspend override fun convertForSend(context: PipelineContext<Any, ApplicationCall>, value: Any): Any? {
                        if (value !is Wrapper) return null
                        return TextContent("[${value.value}]", customContentType)
                    }

                    suspend override fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
                        val type = context.subject.type
                        val incoming = context.subject.value
                        if (type != Wrapper::class || incoming !is IncomingContent) return null
                        return Wrapper(incoming.readText().removeSurrounding("[", "]"))
                    }
                })
            }

            application.routing {
                get("/") {
                    call.respond(Wrapper("OK"))
                }
                post("/") {
                    val text = call.receive<Wrapper>().value
                    call.respond(Wrapper("OK: $text"))
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

            // Unacceptable
            handleRequest(HttpMethod.Get, "/") {
            }.let { call ->
                assertEquals(HttpStatusCode.NotAcceptable, call.response.status())
                assertNull(call.response.headers[HttpHeaders.ContentType])
                assertNull(call.response.content)
            }

            // Post
            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, customContentType.toString())
                addHeader(HttpHeaders.Accept, customContentType.toString())
                body = "[The Text]"
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK: The Text]", call.response.content)
            }

            // Post with charset
            handleRequest(HttpMethod.Post, "/") {
                addHeader(HttpHeaders.ContentType, customContentType.withCharset(Charsets.UTF_8).toString())
                addHeader(HttpHeaders.Accept, customContentType.toString())
                body = "[The Text]"
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals(customContentType, call.response.contentType().withoutParameters())
                assertEquals("[OK: The Text]", call.response.content)
            }
        }
    }
}