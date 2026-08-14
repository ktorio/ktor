/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.scalar

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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScalarTest {

    private val sampleItem = Item("Scalar Test", 42)

    @Test
    fun testScalarFromResources() = testApplication {
        routing {
            scalarUI("scalar")
        }

        val response = client.get("/scalar").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Scalar API Reference</title>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
              </head>
              <body>
                <div id="app"></div>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                <script>Scalar.createApiReference('#app', {
                spec: { url: '/scalar/documentation.yaml' },
                theme: 'purple',
                layout: 'modern',
                showSidebar: true
            });</script>
              </body>
            </html>

            """.trimIndent(),
            response
        )
    }

    @Test
    fun testScalarCustomConfiguration() = testApplication {
        routing {
            scalarUI("scalar") {
                theme = "saturn"
                layout = "classic"
                showSidebar = false
                faviconLocation = "https://example.com/favicon.ico"
                customStyle("https://example.com/custom.css")
            }
        }

        val response = client.get("/scalar").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Scalar API Reference</title>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <link href="https://example.com/custom.css" rel="stylesheet">
                <link href="https://example.com/favicon.ico" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="app"></div>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                <script>Scalar.createApiReference('#app', {
                spec: { url: '/scalar/documentation.yaml' },
                theme: 'saturn',
                layout: 'classic',
                showSidebar: false
            });</script>
              </body>
            </html>

            """.trimIndent(),
            response
        )
    }

    @Test
    fun testScalarCustomRemotePath() = testApplication {
        routing {
            scalarUI("scalar") {
                remotePath = "custom-spec.yaml"
            }
        }

        val response = client.get("/scalar").bodyAsText()
        assertContains(response, "spec: { url: '/scalar/custom-spec.yaml' }")
    }

    @Test
    fun testScalarFileIsServed() = testApplication {
        routing {
            scalarUI("openapi")
        }

        val response = client.get("/openapi/documentation.yaml")
        val body = response.bodyAsText()
        assertEquals("application/yaml", response.contentType().toString())
        assertEquals("hello:\n  world".filter { it.isLetterOrDigit() }, body.filter { it.isLetterOrDigit() })
    }

    @Test
    fun testScalarFileResolvedFromRouting() = testApplication {
        routing {
            route("/api") {
                @OptIn(ExperimentalKtorApi::class)
                route("/items") {
                    get {
                        call.respond(listOf(sampleItem))
                    }.describe {
                        summary = "Get items"
                        responses {
                            HttpStatusCode.OK {
                                schema = jsonSchema<List<Item>>()
                            }
                        }
                    }
                }
            }

            scalarUI("/scalar") {
                info = OpenApiInfo("Items API from routes", "1.0.0")
                source = OpenApiDocSource.Routing(
                    contentType = ContentType.Application.Yaml,
                    schemaInference = ReflectionJsonSchemaInference.Default,
                )
            }
        }

        client.get("/scalar/documentation.yaml").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Items API from routes")
            assertContains(responseText, "Get items")
        }
    }

    @Test
    fun testScalarMissingSpecErrorResponseDoesNotLeakSource() = testApplication {
        routing {
            scalarUI("scalar") {
                source = OpenApiDocSource.File("non_existent_file.yaml")
            }
        }

        val response = client.get("/scalar/documentation.yaml")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())

        val body = response.bodyAsText()
        assertContains(body, "OpenAPI Specification Not Found")
        assertFalse(body.contains("non_existent_file.yaml"), "Server source path must not leak to client")
    }
}

@Serializable
private data class Item(val name: String, val value: Int)
