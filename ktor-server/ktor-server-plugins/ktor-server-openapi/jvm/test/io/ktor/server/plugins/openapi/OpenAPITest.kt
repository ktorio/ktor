/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.reflect.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import org.slf4j.LoggerFactory
import kotlin.test.*

class OpenAPITest {

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
    fun `resolves from file and routing sources`() = testApplication {
        install(ContentNegotiation) {
            json()
        }
        routing {
            @OptIn(ExperimentalKtorApi::class)
            route("/api") {
                route("/books") {
                    get {
                        call.respond(listOf(sampleBook))
                    }.describe {
                        summary = descriptions[0]
                    }
                    get("/{id}") {
                        call.respond(sampleBook)
                    }.describe {
                        summary = descriptions[1]
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

            // Use the default documentation.yaml file
            route("/default") {
                openAPI("docs") {
                    outputPath = "docs/files"
                }
            }

            // Use the routing tree
            route("/routes") {
                openAPI("docs") {
                    outputPath = "docs/routes"
                    info = OpenApiInfo("Books API from routes", "1.0.0")
                    source = OpenApiDocSource.Routing(
                        contentType = ContentType.Application.Json,
                        schemaInference = ReflectionJsonSchemaInference.Default,
                    )
                }
            }
        }

        client.get("/default/docs").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Books API from file")
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }

        client.get("/routes/docs").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Books API from routes")
            assertFalse("/routes/docs" in responseText)
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }
    }

    @Test
    fun `warns when OpenAPI document is 3_1`() = testApplication {
        val messages = captureTestLogMessages {
            routing {
                openAPI("docs") {
                    outputPath = "docs/warn-31"
                }
            }
            startApplication()
        }

        assertTrue(
            messages.any { it.startsWith("OpenAPI document version is") && it.contains("3.1.1") },
            "Expected OpenAPI 3.1 codegen warning, got: $messages"
        )
    }

    @Test
    fun `does not warn when OpenAPI document is 3_0`() = testApplication {
        val messages = captureTestLogMessages {
            routing {
                openAPI("docs") {
                    outputPath = "docs/no-warn-30"
                    source = OpenApiDocSource.Text(
                        """
                        openapi: "3.0.3"
                        info:
                          title: Sample
                          version: "1.0.0"
                        paths:
                          /health:
                            get:
                              summary: Health check
                              responses:
                                "200":
                                  description: OK
                        components:
                          schemas: {}
                        """.trimIndent(),
                        contentType = ContentType.Application.Yaml,
                    )
                }
            }
            startApplication()
        }

        assertFalse(
            messages.any { it.startsWith("OpenAPI document version is") },
            "Did not expect OpenAPI 3.1 codegen warning, got: $messages"
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

data class Book(
    val title: String,
    val author: String
)
