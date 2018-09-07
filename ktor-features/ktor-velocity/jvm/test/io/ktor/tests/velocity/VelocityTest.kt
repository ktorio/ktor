package io.ktor.tests.velocity

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.velocity.*
import org.apache.velocity.runtime.resource.loader.*
import org.apache.velocity.runtime.resource.util.*
import org.junit.Test
import java.util.zip.*
import kotlin.test.*

class VelocityTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()
            application.install(ConditionalHeaders)
            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(VelocityContent("test.vl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
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
                    call.respond(VelocityContent("test.vl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
            }.response.let { response ->
                val content = GZIPInputStream(response.byteContent!!.inputStream()).reader().readText()
                @Suppress("DEPRECATION_ERROR")
                assert(content.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
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
                    call.respond(VelocityContent("test.vl", model))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION_ERROR")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
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
                    call.respondTemplate("test.vl", model)
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
        val bax = "$"

        install(Velocity) {
            setProperty("resource.loader", "string")
            addProperty("string.resource.loader.class", StringResourceLoader::class.java.name)
            addProperty("string.resource.loader.repository.static", "false")
            init() // need to call `init` before trying to retrieve string repository

            val repo = getApplicationAttribute(StringResourceLoader.REPOSITORY_NAME_DEFAULT) as StringResourceRepository
            repo.putStringResource("test.vl", """
                        <p>Hello, ${bax}id</p>
                        <h1>${bax}title</h1>
                    """.trimIndent())
        }
    }
}
