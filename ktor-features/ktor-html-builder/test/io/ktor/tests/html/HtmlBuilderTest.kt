package io.ktor.tests.html

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.html.*
import org.junit.Test
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

        handleRequest(HttpMethod.Get, "/?name=John").response.let { response ->
            assertNotNull(response.content)
            val lines = response.content!!
            assertEquals("""<!DOCTYPE html>
<html>
  <body>
    <h1>Hello, John</h1>
  </body>
</html>
""", lines)
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
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

        handleRequest(HttpMethod.Get, "/?name=John").response.let { response ->
            assertNotNull(response.content)
            assertEquals(HttpStatusCode.NotImplemented, response.status())
            val lines = response.content!!
            assertEquals("""<!DOCTYPE html>
<html>
  <body>
    <h1>This feature is not implemented yet</h1>
  </body>
</html>
""", lines)
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }
}
