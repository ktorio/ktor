/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.annotate.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
            val apiRoute = route("/api") {
                route("/books") {
                    get {
                        call.respond(listOf(sampleBook))
                    }.annotate {
                        summary = descriptions[0]
                    }
                    get("/{id}") {
                        call.respond(sampleBook)
                    }.annotate {
                        summary = descriptions[1]
                    }
                    post {
                        call.respond(HttpStatusCode.Created)
                    }.annotate {
                        summary = descriptions[2]
                    }
                    put("/{id}") {
                        call.respond(HttpStatusCode.NoContent)
                    }.annotate {
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
                    source = OpenApiDocSource.RoutingSource(
                        contentType = ContentType.Application.Json,
                        schemaInference = ReflectionJsonSchemaInference.Default,
                        routes = { apiRoute.descendants() },
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
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }
    }
}

data class Book(
    val title: String,
    val author: String
)
