/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationSerializationTests {

    private val jsonFormat = Json {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun `operation with parameters`() {
        val operation = Operation.build {
            summary = "Get articles"
            parameters {
                path("id") {
                    description = "Article ID"
                    schema = jsonSchema<Int>()
                }
                query("limit") {
                    description = "Page limit"
                    schema = jsonSchema<Int>()
                }
                header("X-Api-Key") {
                    description = "API Key"
                    required = true
                    schema = jsonSchema<String>()
                }
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `operation with request body`() {
        val operation = Operation.build {
            summary = "Create article"
            requestBody {
                description = "Article to create"
                required = true
                jsonSchema = jsonSchema<Article>()
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `operation with security requirements`() {
        val operation = Operation.build {
            summary = "Protected endpoint"
            security {
                basic()
                oauth2("read:articles", "write:articles")
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `operation with tags and external docs`() {
        val operation = Operation.build {
            tag("articles")
            tag("public")
            operationId = "getArticles"
            deprecated = true
            externalDocs = ExternalDocs(
                url = "https://example.com/docs",
                description = "API documentation"
            )
        }

        checkSerialization(operation)
    }

    @Test
    fun `operation with custom servers`() {
        val operation = Operation.build {
            summary = "Get articles"
            servers {
                server("https://api.example.com") {
                    description = "Production server"
                }
                server("https://staging-api.example.com") {
                    description = "Staging server"
                }
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `complete operation with all fields`() {
        val operation = Operation.build {
            tag("articles")
            summary = "Create and get article"
            description = "Creates a new article and returns it"
            operationId = "createArticle"
            deprecated = false

            parameters {
                header("X-Request-ID") {
                    description = "Request ID for tracing"
                    schema = jsonSchema<String>()
                }
            }

            requestBody {
                description = "Article data"
                required = true
                jsonSchema = jsonSchema<Article>()
            }

            responses {
                HttpStatusCode.Created {
                    description = "Article created successfully"
                    jsonSchema = jsonSchema<Article>()
                }
                HttpStatusCode.BadRequest {
                    description = "Invalid input"
                    ContentType.Text.Plain()
                }
            }

            security {
                oauth2("write:articles")
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `operation with extension properties`() {
        val operation = Operation.build {
            summary = "Get articles with extensions"
            operationId = "getArticles"

            // Add custom extension properties
            extension("x-internal-notes", "This endpoint is monitored")
            extension("x-feature-flags", listOf("feature-a", "feature-b"))

            responses {
                HttpStatusCode.OK {
                    description = "Success response"
                    ContentType.Application.Json {
                        schema = jsonSchema<List<Article>>()
                    }

                    // Extensions on response level
                    extension("x-cache-ttl", 300)
                    extension("x-response-time-ms", 50)
                }
            }

            parameters {
                query("limit") {
                    description = "Page limit"
                    schema = jsonSchema<Int>()
                    // Extensions on parameter level
                    extension("x-max-value", 1000)
                }
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `server with extension properties`() {
        val operation = Operation.build {
            summary = "Endpoint with custom servers"

            servers {
                server("https://api.example.com") {
                    description = "Production server"
                    extension("x-region", "us-east-1")
                    extension("x-load-balancer", "ALB-prod-123")
                }
            }
        }

        checkSerialization(operation)
    }

    @Test
    fun `request body with extension properties`() {
        val operation = Operation.build {
            summary = "Create article"

            requestBody {
                description = "Article data"
                required = true
                jsonSchema = jsonSchema<Article>()
                extension("x-validation-schema", "article-v1")
                extension("x-max-size-bytes", 1048576) // 1MB
            }
        }

        checkSerialization(operation)
    }

    private fun checkSerialization(operation: Operation) {
        val json = jsonFormat.encodeToString(operation)
        val parsed = jsonFormat.decodeFromString<Operation>(json)
        assertEquals(json, jsonFormat.encodeToString(parsed))
    }
}

@Serializable
class Article(
    val title: String,
    val description: String,
    val author: String,
    val tags: List<String> = emptyList(),
    val content: String,
)
