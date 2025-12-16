/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.apikey.apiKey
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.oauth
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouteAnnotationApiTest {

    companion object {
        private val expected = $$"""
            {
              "openapi": "3.1.1",
              "info": {
                "title": "Test API",
                "version": "1.0.0"
              },
              "paths": {
                "/messages": {
                  "get": {
                    "summary": "get messages",
                    "description": "Retrieves a list of messages.",
                    "parameters": [
                      {
                        "name": "q",
                        "in": "query",
                        "description": "An encoded query",
                        "content": {
                          "text/plain": {}
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
                                "$ref": "#/components/schemas/Message"
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
              },
              "components": {
                "schemas": {
                  "Message": {
                    "type": "object",
                    "title": "io.ktor.annotate.Message",
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
            }
        """.trimIndent()
    }

    val testMessage = Message(1L, "Hello, world!", 16777216000)

    @OptIn(ExperimentalSerializationApi::class)
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
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot,
                    ).let {
                        it.copy(
                            paths = it.paths - "/routes"
                        )
                    }
                )
            }

            // example REST API route
            get("/messages") {
                call.respond(listOf(testMessage))
            }.annotate {
                parameters {
                    query("q") {
                        description = "An encoded query"
                        required = false
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "A list of messages"
                        schema = jsonSchema<List<Message>>()
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

        val openApiSpec = jsonFormat.decodeFromString<OpenApiSpecification>(responseText)
        val pathItems: Map<String, PathItem> = openApiSpec.paths
        assertEquals(1, pathItems.size)
    }

    @Test
    fun annotateAddition() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        routing {
            get("/routes") {
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot,
                    ).let {
                        it.copy(
                            paths = it.paths - "/routes"
                        )
                    }
                )
            }
            get("/messages") {
                call.response.header("X-Sample-Message", "test")
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
                        ContentType.Text.Plain()
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
                val pathItems = call.application.routingRoot.findPathItems() - "/routes"
                call.respond(pathItems)
            }
            route("/messages") {
                get {
                    call.respond(listOf(testMessage))
                }.annotate {
                    summary = "get messages"
                    description = "Retrieves a list of messages."

                    parameters {
                        @Suppress("DEPRECATION")
                        parameter("q") {
                            required = true
                            description = "Message query"
                        }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "A list of messages"
                            schema = jsonSchema<List<Message>>()
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
                        schema = jsonSchema<String>()
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
            assertEquals(KotlinxJsonSchemaInference.jsonSchema<String>(), schema?.valueOrNull())
        }

        val responses = operation.responses
        assertNotNull(responses, "Responses were null")
        val okResponse = responses.responses?.get(HttpStatusCode.OK.value)?.valueOrNull()
        assertNotNull(okResponse, "OK response is missing")
        assertEquals("A list of messages", okResponse.description)
        assertEquals("child", okResponse.extensions?.get("x-bonus")?.deserialize(String.serializer()))
        assertEquals(
            KotlinxJsonSchemaInference.jsonSchema<List<Message>>(),
            okResponse.content?.get(ContentType.Application.Json)?.schema?.valueOrNull()
        )
        val badRequestResponse = responses.responses?.get(HttpStatusCode.BadRequest.value)?.valueOrNull()
        assertNotNull(badRequestResponse, "Bad request response is missing")
        assertNotNull(
            badRequestResponse.content?.get(ContentType.Text.Plain),
            "Bad request response content is missing"
        )
    }

    @Test
    fun testAutomaticSecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            basic("basic-auth") {
                validate { UserIdPrincipal("user") }
            }
            bearer("bearer-auth") {}
        }
        routing {
            get("/routes") {
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot
                    )
                )
            }

            authenticate("basic-auth") {
                get("/basic") {
                    call.respond("authenticated")
                }
            }

            authenticate("bearer-auth") {
                get("/bearer") {
                    call.respond("authenticated")
                }
            }

            authenticate("basic-auth", "bearer-auth") {
                get("/multiple") {
                    call.respond("authenticated")
                }
            }

            authenticate("basic-auth", strategy = AuthenticationStrategy.Required) {
                get("/required") {
                    call.respond("authenticated")
                }
            }

            authenticate("basic-auth", strategy = AuthenticationStrategy.Optional) {
                get("/optional") {
                    call.respond("authenticated")
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = Json.decodeFromString<OpenApiSpecification>(responseText)

        // Ensure security schemes are serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")
        assertEquals(2, schemes.size)
        val basicScheme = schemes["basic-auth"]!!.valueOrNull() as HttpSecurityScheme
        assertEquals("HTTP Basic Authentication", basicScheme.description)
        val bearerScheme = schemes["bearer-auth"]!!.valueOrNull() as HttpSecurityScheme
        assertEquals("bearer", bearerScheme.scheme)
        assertEquals(null, bearerScheme.bearerFormat)

        // Test basic auth
        val basicOperation = openApiSpec.paths["/basic"]?.get
        assertNotNull(basicOperation, "Basic auth operation should exist")
        assertEquals(1, basicOperation.security?.size)
        val basicSecurity = basicOperation.security?.first()!!
        assertEquals(1, basicSecurity.size)
        assertEquals(true, basicSecurity.containsKey("basic-auth"))
        assertEquals(emptyList(), basicSecurity["basic-auth"])

        // Test bearer auth
        val bearerOperation = openApiSpec.paths["/bearer"]?.get
        assertNotNull(bearerOperation, "Bearer auth operation should exist")
        assertNotNull(bearerOperation.security, "Security should be defined")
        assertEquals(1, bearerOperation.security?.size)
        val bearerSecurity = bearerOperation.security?.first()!!
        assertEquals(1, bearerSecurity.size)
        assertTrue(bearerSecurity.containsKey("bearer-auth"))
        assertEquals(emptyList(), bearerSecurity["bearer-auth"])

        // Test multiple auth (FirstSuccessful - OR relationship)
        val multipleOperation = openApiSpec.paths["/multiple"]?.get
        assertNotNull(multipleOperation, "Multiple auth operation should exist")
        assertNotNull(multipleOperation.security, "Security should be defined")

        // FirstSuccessful creates separate security requirements (OR)
        val basicSecurity2 = multipleOperation.security!!
        assertEquals(2, basicSecurity2.size)
        assertTrue(basicSecurity2.any { it.containsKey("basic-auth") && it["basic-auth"]!!.isEmpty() })
        assertTrue(basicSecurity2.any { it.containsKey("bearer-auth") && it["bearer-auth"]!!.isEmpty() })

        // Test required auth
        val requiredOperation = openApiSpec.paths["/required"]?.get
        assertNotNull(requiredOperation, "Required auth operation should exist")
        assertNotNull(requiredOperation.security, "Security should be defined")
        // Required creates a single requirement with all schemes (AND)
        assertEquals(1, requiredOperation.security?.size)
        assertEquals(1, requiredOperation.security?.first()?.size)
        assertEquals(true, requiredOperation.security?.first()?.containsKey("basic-auth"))

        // Test optional auth
        val optionalOperation = openApiSpec.paths["/optional"]?.get
        assertNotNull(optionalOperation, "Optional auth operation should exist")
        assertNotNull(optionalOperation.security, "Security should be defined")

        val optionalSecurity = optionalOperation.security?.first()!!
        assertEquals(true, optionalSecurity["basic-auth"]?.isEmpty())
    }

    @Test
    fun testManualSecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            apiKey("api-key") {
                headerName = "X-API-KEY"
                validate { UserIdPrincipal(it) }
            }
        }
        application {
            registerApiKeySecurityScheme(
                name = "api-key",
                keyName = "X-API-KEY",
                keyLocation = SecuritySchemeIn.HEADER,
            )
        }
        routing {
            authenticate("api-key") {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            get("/routes") {
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot,
                    )
                )
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = Json.decodeFromString<OpenApiSpecification>(responseText)

        val operation = openApiSpec.paths["/test"]?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("api-key"))
        assertEquals(emptyList(), security[0]["api-key"])
    }

    @Test
    fun testOAuth2SecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            oauth("oauth") {
                urlProvider = { "http://localhost/callback" }
                settings = OAuthServerSettings.OAuth2ServerSettings(
                    name = "test",
                    authorizeUrl = "https://auth.example.com/authorize",
                    accessTokenUrl = "https://auth.example.com/token",
                    clientId = "client",
                    clientSecret = "secret",
                    defaultScopes = listOf("profile", "email")
                )
                client = this@testApplication.client
            }
        }

        routing {
            authenticate("oauth") {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            get("/routes") {
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot,
                    )
                )
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()

        val openApiSpec = Json.decodeFromString<OpenApiSpecification>(responseText)

        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")
        assertEquals(1, schemes.size)
        val oauthScheme = schemes["oauth"]?.valueOrNull() as OAuth2SecurityScheme
        assertNotNull(oauthScheme.flows?.authorizationCode)

        val operation = openApiSpec.paths["/test"]?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("oauth"))
        assertEquals(setOf("profile", "email"), security[0]["oauth"]?.toSet())
    }
}

@Serializable
data class Message(
    val id: Long,
    val content: String,
    val timestamp: Long
)
