/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.reflect.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.test.*

class SwaggerTest {

    private val sampleBook = Book(
        "The Hitchhiker's Guide to the Galaxy",
        "Douglas Adams"
    )
    private val descriptions = listOf(
        "List all books",
        "Get a book by id",
        "Create a book",
        "Update a book"
    )

    @Test
    fun testSwaggerFromResources() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>

            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerAllowDeepLinking() = testApplication {
        routing {
            swaggerUI("swagger") {
                deepLinking = true
            }
        }

        val response = client.get("/swagger").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFromResourcesWithDocExpansion() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "list")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout',
                    docExpansion: 'list'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testInvalidDocExpansionIgnored() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "'; alert('Hey')")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFileIsServed() = testApplication {
        routing {
            swaggerUI("openapi")
        }

        val response = client.get("/openapi/documentation.yaml")
        val body = response.bodyAsText()
        assertEquals("application/yaml", response.contentType().toString())
        assertEquals("hello:\n  world".filter { it.isLetterOrDigit() }, body.filter { it.isLetterOrDigit() })
    }

    @Test
    fun testCustomFavicon() = testApplication {
        routing {
            swaggerUI("swagger") {
                faviconLocation = "https://www.google.com/favicon.ico"
            }
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "list")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui.css" rel="stylesheet">
                <link href="https://www.google.com/favicon.ico" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.31.0/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout',
                    docExpansion: 'list'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun `swagger file resolved from routing`() = testApplication {
        routing {
            route("/api") {
                @OptIn(ExperimentalKtorApi::class)
                route("/books") {
                    get {
                        call.respond(listOf(sampleBook))
                    }.describe {
                        summary = descriptions[0]
                        responses {
                            HttpStatusCode.OK {
                                schema = jsonSchema<List<Book>>()
                            }
                        }
                    }
                    get("/{id}") {
                        call.respond(sampleBook)
                    }.describe {
                        summary = descriptions[1]
                        responses {
                            HttpStatusCode.OK {
                                schema = jsonSchema<Book>()
                            }
                        }
                    }
                    post {
                        call.respond(HttpStatusCode.Created)
                    }.describe {
                        summary = descriptions[2]
                    }
                    put("/{id}") {
                        call.respond(HttpStatusCode.NoContent)
                    }.describe {
                        summary = descriptions[3]
                    }
                }
            }

            swaggerUI("/swagger") {
                info = OpenApiInfo("Books API from routes", "1.0.0")
                source = OpenApiDocSource.Routing(
                    contentType = ContentType.Application.Yaml,
                    schemaInference = ReflectionJsonSchemaInference.Default,
                )
            }
        }

        client.get("/swagger/documentation.yaml").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Books API from routes")
            assertContains(
                responseText,
                """
              required:
              - author
              - title
                """.trimIndent().prependIndent("      ")
            )
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }
    }

    @Test
    fun testSwaggerServesOauthRedirectPage() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger/oauth2-redirect.html").bodyAsText()
        assertEquals(
            """<!DOCTYPE html>
<html>
  <body>
    <script src="https://unpkg.com/swagger-ui-dist@5.31.0/oauth2-redirect.js"></script>
  </body>
</html>
""",
            response
        )
    }

    @Test
    fun testSwaggerCustomOauth2RedirectUrl() = testApplication {
        routing {
            swaggerUI("swagger") {
                oauth2RedirectUrl = "https://api.example.com/custom/oauth2-redirect.html"
            }
        }

        val response = client.get("/swagger").bodyAsText()
        assertContains(response, "oauth2RedirectUrl: 'https://api.example.com/custom/oauth2-redirect.html'")
    }

    @Test
    fun `warns when OpenAPI 3_1 document is served with Swagger UI below 5`() = testApplication {
        val messages = captureTestLogMessages {
            routing {
                swaggerUI("swagger") {
                    version = "4.15.5"
                    source = OpenApiDocSource.Text(
                        """
                        openapi: "3.1.1"
                        info:
                          title: Sample
                          version: "1.0.0"
                        paths: {}
                        """.trimIndent(),
                        contentType = ContentType.Application.Yaml,
                    )
                }
            }
            client.get("/swagger/documentation.yaml")
        }

        assertTrue(
            messages.any {
                it.contains("3.1.1") && it.contains("4.15.5") && it.contains("does not support OpenAPI 3.1")
            },
            "Expected Swagger UI OpenAPI 3.1 warning, got: $messages"
        )
    }

    @Test
    fun `does not warn when OpenAPI 3_0 document is served with Swagger UI below 5`() = testApplication {
        val messages = captureTestLogMessages {
            routing {
                swaggerUI("swagger") {
                    version = "4.15.5"
                    source = OpenApiDocSource.Text(
                        """
                        openapi: "3.0.3"
                        info:
                          title: Sample
                          version: "1.0.0"
                        paths: {}
                        """.trimIndent(),
                        contentType = ContentType.Application.Yaml,
                    )
                }
            }
            client.get("/swagger/documentation.yaml")
        }

        assertFalse(
            messages.any { it.contains("does not support OpenAPI 3.1") },
            "Did not expect Swagger UI OpenAPI 3.1 warning, got: $messages"
        )
    }

    @Test
    fun `does not warn when OpenAPI 3_1 document is served with Swagger UI 5 or later`() = testApplication {
        val messages = captureTestLogMessages {
            routing {
                swaggerUI("swagger") {
                    source = OpenApiDocSource.Text(
                        """
                        openapi: "3.1.1"
                        info:
                          title: Sample
                          version: "1.0.0"
                        paths: {}
                        """.trimIndent(),
                        contentType = ContentType.Application.Yaml,
                    )
                }
            }
            client.get("/swagger/documentation.yaml")
        }

        assertFalse(
            messages.any { it.contains("does not support OpenAPI 3.1") },
            "Did not expect Swagger UI OpenAPI 3.1 warning, got: $messages"
        )
    }
}

private inline fun captureTestLogMessages(block: () -> Unit): List<String> {
    val logger = LoggerFactory.getLogger("io.ktor.test") as Logger
    val previousLevel = logger.level
    logger.level = Level.WARN
    val listAppender = ListAppender<ILoggingEvent>()
    logger.addAppender(listAppender)
    listAppender.start()
    try {
        block()
    } finally {
        listAppender.stop()
        logger.detachAppender(listAppender)
        logger.level = previousLevel
    }
    return listAppender.list.map { it.message }
}

@Serializable
data class Book(val title: String, val author: String)
