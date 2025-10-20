/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RouteAnnotationApiTest {

    companion object {
        private val expected = """
            {
              "/messages": {
                "get": {
                  "summary": "get messages",
                  "description": "Retrieves a list of messages.",
                  "parameters": [
                    {
                      "name": "q",
                      "in": "query",
                      "description": "An encoded query",
                      "schema": {
                        "type": "string"
                      }
                    }
                  ],
                  "responses": {
                    "200": {
                      "description": "A list of messages",
                      "content": {
                        "application/json": {
                          "schema": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": [
                                "id",
                                "content",
                                "timestamp"
                              ],
                              "properties": {
                                "id": {
                                  "type": "integer"
                                },
                                "content": {
                                  "type": "string"
                                },
                                "timestamp": {
                                  "type": "integer"
                                }
                              }
                            }
                          }
                        }
                      },
                      "x-sample-message": {
                        "id": 1,
                        "content": "Hello, world!",
                        "timestamp": 16777216000
                      }
                    },
                    "400": {
                      "description": "Invalid query",
                      "content": {
                        "text/plain": {}
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
    val testMessage = Message(1L, "Hello, world!", 16777216000)
    val jsonFormat = Json {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun routeAnnotationIntrospection() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        routing {
            // get all path items
            get("/routes") {
                val routes = call.application.routingRoot.findPathItems() - "/routes"
                call.respond(routes)
            }

            // example REST API route
            get("/messages") {
                call.respond(listOf(testMessage))
            }.annotate {
                parameters {
                    query("q") {
                        description = "An encoded query"
                        required = false
                        schema = jsonSchema<String>()
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "A list of messages"
                        jsonSchema = jsonSchema<List<Message>>()
                        extension("x-sample-message", testMessage)
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid query"
                        ContentType.Text.Plain()
                    }
                }
                summary = "get messages"
                description = "Retrieves a list of messages."
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        assertEquals(expected, responseText)
        // should not appear
        assertFalse("extensions" in responseText)

        val pathItems: Map<String, PathItem> = jsonFormat.decodeFromString(responseText)
        assertEquals(1, pathItems.size)
    }
}

@Serializable
data class Message(
    val id: Long,
    val content: String,
    val timestamp: Long
)
