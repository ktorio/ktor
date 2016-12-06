package org.jetbrains.ktor.tests.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class MenuTemplate : Template<FlowContent> {
    val item = PlaceholderList<FlowContent, FlowContent>()
    override fun FlowContent.apply() {
        if (!item.isEmpty()) {
            ul {
                each(item) {
                    li {
                        if (it.first) b {
                            insert(it)
                        } else {
                            insert(it)
                        }
                    }
                }
            }
        }
    }
}

class MainTemplate : Template<HTML> {
    val content = Placeholder<BODY>()
    val menu = TemplatePlaceholder<MenuTemplate>()
    override fun HTML.apply() {
        head {
            title { +"Template" }
        }
        body {
            h1 {
                insert(content)
            }
            insert(MenuTemplate(), menu)
        }
    }
}

class HtmlTemplateTest {
    @Test
    fun testTemplate() = withTestApplication {
        application.routing {
            get("/") {
                val name = call.parameters["name"]
                call.respondHtmlTemplate(MainTemplate()) {
                    content {
                        +"Hello, $name"
                    }
                    menu {
                        item { +"One" }
                        item { +"Two" }
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/?name=John").response.let { response ->
            assertNotNull(response.content)
            val lines = response.content!!
            assertEquals("""<html>
  <head>
    <title>Template</title>
  </head>
  <body>
    <h1>Hello, John</h1>
    <ul>
      <li><b>One</b></li>
      <li>Two</li>
    </ul>
  </body>
</html>
""", lines)
            assertEquals(ContentType.Text.Html, ContentType.parse(assertNotNull(response.headers[HttpHeaders.ContentType])))
        }
    }
}