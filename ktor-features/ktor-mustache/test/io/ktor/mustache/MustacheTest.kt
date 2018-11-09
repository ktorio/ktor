package io.ktor.mustache

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.Compression
import io.ktor.features.ConditionalHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.withCharset
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MustacheTest {

    @Test
    fun `Fill template and expect correct rendered content`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
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
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
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
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel, "e"))
                }
            }

            assertEquals("e", handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }


    @Test
    fun `Render empty model`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {
                get("/") {
                    call.respond(MustacheContent(TemplateWithoutPlaceholder, null, "e"))
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
            application.setupMustache()
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
    fun `Render template without eTag`() {
        withTestApplication {
            application.setupMustache()
            application.install(ConditionalHeaders)

            application.routing {

                get("/") {
                    call.respond(MustacheContent(TemplateWithPlaceholder, DefaultModel))
                }
            }

            assertEquals(null, handleRequest(HttpMethod.Get, "/").response.headers[HttpHeaders.ETag])
        }
    }

    private fun Application.setupMustache() {
        install(Mustache) {
            DefaultMustacheFactory()
        }
    }

    companion object {
        private val DefaultModel = mapOf("id" to 1, "title" to "Hello World!")

        private val TemplateWithPlaceholder = "withPlaceholder.mustache"
        private val TemplateWithoutPlaceholder = "withoutPlaceholder.mustache"
    }
}
