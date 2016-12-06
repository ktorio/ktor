package org.jetbrains.ktor.tests.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class HtmlBuilderTest {
    @Test
    fun testName() = withTestApplication {
        application.routing {
            get("/") {
                val name = call.parameters["name"]
                call.respondHtml {
                    body {
                        h1 {
                            +"Hello, $name"
                        }
                    }
                }
            }
        }

        handleRequest(org.jetbrains.ktor.http.HttpMethod.Companion.Get, "/?name=John").response.let { response ->
            kotlin.test.assertNotNull(response.content)
            val lines = response.content!!
            assertEquals("""<html>
  <body>
    <h1>Hello, John</h1>
  </body>
</html>
""", lines)
            assertEquals(ContentType.Text.Html, ContentType.Companion.parse(assertNotNull(response.headers[HttpHeaders.ContentType])))
        }
    }

    @Test
    fun testError() = withTestApplication {
        application.install(StatusPages) {
            exception<NotImplementedError> {
                call.respondHtml(HttpStatusCode.NotImplemented) {
                    body {
                        h1 {
                            +"This feature is not implemented yet"
                        }
                    }
                }
            }

        }

        application.routing {
            get("/") {
                TODO()
            }
        }

        handleRequest(org.jetbrains.ktor.http.HttpMethod.Companion.Get, "/?name=John").response.let { response ->
            assertNotNull(response.content)
            assertEquals(HttpStatusCode.NotImplemented, response.status())
            val lines = response.content!!
            assertEquals("""<html>
  <body>
    <h1>This feature is not implemented yet</h1>
  </body>
</html>
""", lines)
            assertEquals(ContentType.Text.Html, ContentType.Companion.parse(assertNotNull(response.headers[HttpHeaders.ContentType])))
        }
    }
}
