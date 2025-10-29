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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

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

    @Test
    fun annotateAddition() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        routing {
            get("/routes") {
                val routes = call.application.routingRoot.findPathItems() - "/routes"
                call.respond(routes)
            }
            get("/messages") {
                call.respond(listOf(testMessage))
            }.annotate {
                parameters {
                    header("X-First") {
                        description = "First header"
                    }
                }
            }.annotate {
                parameters {
                    header("X-Second") {
                        description = "Second header"
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        assertContains(responseText, "\"X-First\"")
        assertContains(responseText, "\"X-Second\"")
    }

    @Test
    fun annotateMerging() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        routing {
            get("/routes") {
                val routes = call.application.routingRoot.findPathItems() - "/routes"
                call.respond(routes)
            }
            route("/messages") {
                get {
                    call.respond(listOf(testMessage))
                }.annotate {
                    summary = "get messages"
                    description = "Retrieves a list of messages."

                    parameters {
                        query("q") {
                            required = true
                            schema = jsonSchema<String>()
                            description = "Message query"
                        }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "A list of messages"
                            jsonSchema = jsonSchema<List<Message>>()
                            extension("x-bonus", "child")
                        }
                    }
                }
            }.annotate {
                summary = "parent route"

                parameters {
                    query("q") {
                        required = false
                        description = "A query"
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Some list"
                        extension("x-bonus", "parent")
                    }
                    HttpStatusCode.BadRequest {
                        ContentType.Text.Plain()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val pathItems: Map<String, PathItem> = jsonFormat.decodeFromString(responseText)
        assertEquals(1, pathItems.size)

        val operation = pathItems.values.firstOrNull()?.get
        assertNotNull(operation, "Expect get operation")
        assertEquals("get messages", operation.summary)
        assertEquals("Retrieves a list of messages.", operation.description)

        val parameters = operation.parameters
        assertNotNull(parameters, "Parameters were null")
        assertEquals(1, parameters.size, "Expected a single, merged parameter but got: $parameters")
        with(parameters.single().valueOrNull()!!) {
            assertEquals("q", name)
            assertEquals("Message query", description)
            assertEquals(true, required)
            assertEquals(jsonSchema<String>(), schema?.valueOrNull())
        }

        val responses = operation.responses
        assertNotNull(responses, "Responses were null")
        val okResponse = responses.responses?.get(HttpStatusCode.OK.value)?.valueOrNull()
        assertNotNull(okResponse, "OK response is missing")
        assertEquals("A list of messages", okResponse.description)
        assertEquals("child", okResponse.extensions?.get("x-bonus")?.deserialize(String.serializer()))
        assertEquals(
            jsonSchema<List<Message>>(),
            okResponse.content?.get(ContentType.Application.Json)?.schema?.valueOrNull()
        )
        val badRequestResponse = responses.responses?.get(HttpStatusCode.BadRequest.value)?.valueOrNull()
        assertNotNull(badRequestResponse, "Bad request response is missing")
        assertNotNull(
            badRequestResponse.content?.get(ContentType.Text.Plain),
            "Bad request response content is missing"
        )
    }
}

@Serializable
data class Message(
    val id: Long,
    val content: String,
    val timestamp: Long
)
