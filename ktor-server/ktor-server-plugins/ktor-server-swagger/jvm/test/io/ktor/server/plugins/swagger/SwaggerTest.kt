/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SwaggerTest {
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
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
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
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: true,
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
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
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
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://www.google.com/favicon.ico" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
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
}
