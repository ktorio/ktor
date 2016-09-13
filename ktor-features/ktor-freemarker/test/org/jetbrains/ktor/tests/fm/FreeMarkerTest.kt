package org.jetbrains.ktor.tests.fm

import freemarker.cache.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class FreeMarkerTest {
    @Test
    fun testName() {
        withTestApplication {
            application.setUpTestTemplates()

            application.routing {
                val model = mapOf("id" to 1, "title" to "Hello, World!")

                get("/") {
                    call.respond(FreeMarkerContent("test.ftl", model, "e"))
                }
            }

            handleRequest(HttpMethod.Get, "/").response.let { response ->
                assertNotNull(response.content)
                @Suppress("DEPRECATION")
                assert(response.content!!.lines()) {
                    shouldBe(listOf("<p>Hello, 1</p>", "<h1>Hello, World!</h1>"))
                }
                assertEquals(ContentType.Text.Html, ContentType.Companion.parse(assertNotNull(response.headers[HttpHeaders.ContentType])))
                assertEquals("e", response.headers[HttpHeaders.ETag])
            }
        }
    }

    private fun Application.setUpTestTemplates() {
        val bax = "$"

        install(FreeMarker) {
            templateLoader = StringTemplateLoader().apply {
                putTemplate("test.ftl", """
                        <p>Hello, $bax{id}</p>
                        <h1>$bax{title}</h1>
                    """.trimIndent())
            }
        }
    }
}
