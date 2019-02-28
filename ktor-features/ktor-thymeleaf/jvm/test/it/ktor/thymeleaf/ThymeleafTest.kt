package it.ktor.thymeleaf

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
import io.ktor.thymeleaf.Thymeleaf
import io.ktor.thymeleaf.ThymeleafContent
import io.ktor.thymeleaf.respondTemplate
import org.junit.Test
import org.thymeleaf.templateresolver.StringTemplateResolver
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.shouldBe

class ThymeleafTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(TEMPLATE, model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                (kotlin.test.assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                })
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
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(TEMPLATE, model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                @Suppress("DEPRECATION_ERROR")
                (kotlin.test.assert(content.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                })
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
            application.install(ConditionalHeaders)

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(ThymeleafContent(TEMPLATE, model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                (kotlin.test.assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                })
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
                val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

                get("/") {
                    call.respondTemplate(TEMPLATE, model)
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

    private fun Application.setUpTestTemplates() {
        install(Thymeleaf) {
            setTemplateResolver(StringTemplateResolver())
        }
    }

    companion object {
        val bax = "$"
        private val TEMPLATE = """
            <p>Hello, [[$bax{id}]]</p>
            <h1 th:text="$bax{title}"></h1>
        """.trimIndent()
    }

}
