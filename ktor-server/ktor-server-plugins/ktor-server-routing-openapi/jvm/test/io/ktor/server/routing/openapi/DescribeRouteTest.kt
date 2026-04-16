/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DescribeRouteTest {

    private val jwtSecret = "test-jwt-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    val testMessage = Message.DM(1L, "Hello, world!", 16777216000)

    @OptIn(ExperimentalSerializationApi::class)
    val jsonFormat = Json {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    val yamlFormat = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
        )
    )

    @Test
    fun routeAnnotationIntrospection() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            // get all path items
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants()
                )
            }.hide()

            get("/messages") {
                call.respond(listOf(testMessage))
            }.describe {
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
        val expectedJson = this::class.java.getResource("/expected/openapi.json")!!.readText()
        assertEquals(expectedJson.trim(), responseText)
        // should not appear
        assertFalse("extensions" in responseText)

        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val pathItems: Map<String, ReferenceOr<PathItem>> = openApiSpec.paths
        assertEquals(1, pathItems.size)
    }

    @Test
    fun annotateAddition() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants()
                )
            }.hide()

            get("/messages") {
                call.response.header("X-Sample-Message", "test")
                call.respond(listOf(testMessage))
            }.describe {
                parameters {
                    header("X-First") {
                        description = "First header"
                    }
                }
            }.describe {
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
        assertContains(responseText, "\"summary\": \"\"")
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
                val pathItems = call.application.routingRoot.descendants().mapToPathItems() - "/routes"
                call.respond(pathItems)
            }
            @OptIn(ExperimentalKtorApi::class)
            route("/messages") {
                get {
                    call.respond(listOf(testMessage))
                }.describe {
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
            }.describe {
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
    fun yamlResponse() = testApplication {
        install(ContentNegotiation) {
            serialization(ContentType.Application.Yaml, yamlFormat)
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants()
                )
            }.hide()

            get("/messages") {
                call.respond(listOf(testMessage))
            }.describe {
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
        val expectedYaml = this::class.java.getResource("/expected/openapi.yaml")!!.readText()
        assertEquals(expectedYaml.trim(), responseText)

        val openApiSpec = yamlFormat.decodeFromString<OpenApiDoc>(responseText)
        val pathItems: Map<String, ReferenceOr<PathItem>> = openApiSpec.paths
        assertEquals(1, pathItems.size)
    }

    @Test
    fun parameterOrdering() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants()
                )
            }.hide()

            get("/plugins/{category}/{product}/{version}") {
                call.respondText((1..10).joinToString("\n") { i -> "plugin $i" })
            }.describe {
                parameters {
                    query("expand") {
                        description = "Show all details"
                        required = false
                    }
                }
                summary = "get messages"
                description = "Retrieves a list of messages."
            }
        }

        val routesResponse = client.get("/routes")
        val apiSpec = Json.decodeFromString<OpenApiDoc>(routesResponse.bodyAsText())
        val parameters = apiSpec.paths["/plugins/{category}/{product}/{version}"]
            ?.valueOrNull()?.get?.parameters
            ?.filterIsInstance<ReferenceOr.Value<Parameter>>()
        assertNotNull(parameters, "Parameters not found")
        val parameterNames = parameters.joinToString { it.value.name }
        assertEquals("category, product, version, expand", parameterNames)
    }

    @Test
    fun automaticSecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            basic("basic-auth") {
                validate { UserIdPrincipal("user") }
            }
            bearer("bearer-auth") {}
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants() +
                        call.application.findSecuritySchemes()
                )
            }.hide()

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
        val openApiSpec = Json.decodeFromString<OpenApiDoc>(responseText)

        // Ensure security schemes are serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")
        assertEquals(2, schemes.size)
        val basicScheme = schemes["basic-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals("HTTP Basic Authentication", basicScheme.description)
        val bearerScheme = schemes["bearer-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals("bearer", bearerScheme.scheme)
        assertEquals(null, bearerScheme.bearerFormat)

        // Test basic auth
        val basicOperation = openApiSpec.paths["/basic"]?.valueOrNull()?.get
        assertNotNull(basicOperation, "Basic auth operation should exist")
        assertEquals(1, basicOperation.security?.size)
        val basicSecurity = basicOperation.security?.first()!!
        assertEquals(1, basicSecurity.size)
        assertEquals(true, basicSecurity.containsKey("basic-auth"))
        assertEquals(emptyList(), basicSecurity["basic-auth"])

        // Test bearer auth
        val bearerOperation = openApiSpec.paths["/bearer"]?.valueOrNull()?.get
        assertNotNull(bearerOperation, "Bearer auth operation should exist")
        assertNotNull(bearerOperation.security, "Security should be defined")
        assertEquals(1, bearerOperation.security?.size)
        val bearerSecurity = bearerOperation.security?.first()!!
        assertEquals(1, bearerSecurity.size)
        assertTrue(bearerSecurity.containsKey("bearer-auth"))
        assertEquals(emptyList(), bearerSecurity["bearer-auth"])

        // Test multiple auth (FirstSuccessful - OR relationship)
        val multipleOperation = openApiSpec.paths["/multiple"]?.valueOrNull()?.get
        assertNotNull(multipleOperation, "Multiple auth operation should exist")
        assertNotNull(multipleOperation.security, "Security should be defined")

        // FirstSuccessful creates separate security requirements (OR)
        val basicSecurity2 = multipleOperation.security!!
        assertEquals(2, basicSecurity2.size)
        assertTrue(basicSecurity2.any { it.containsKey("basic-auth") && it["basic-auth"]!!.isEmpty() })
        assertTrue(basicSecurity2.any { it.containsKey("bearer-auth") && it["bearer-auth"]!!.isEmpty() })

        // Test required auth
        val requiredOperation = openApiSpec.paths["/required"]?.valueOrNull()?.get
        assertNotNull(requiredOperation, "Required auth operation should exist")
        assertNotNull(requiredOperation.security, "Security should be defined")
        // Required creates a single requirement with all schemes (AND)
        assertEquals(1, requiredOperation.security?.size)
        assertEquals(1, requiredOperation.security?.first()?.size)
        assertEquals(true, requiredOperation.security?.first()?.containsKey("basic-auth"))

        // Test optional auth
        val optionalOperation = openApiSpec.paths["/optional"]?.valueOrNull()?.get
        assertNotNull(optionalOperation, "Optional auth operation should exist")
        assertNotNull(optionalOperation.security, "Security should be defined")

        val optionalSecurity = optionalOperation.security?.first()!!
        assertEquals(true, optionalSecurity["basic-auth"]?.isEmpty())
    }

    @Test
    fun manualSecurityAnnotations() = testApplication {
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
        @OptIn(ExperimentalKtorApi::class)
        routing {
            authenticate("api-key") {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants() +
                        call.application.findSecuritySchemes()
                )
            }.hide()
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = Json.decodeFromString<OpenApiDoc>(responseText)

        val operation = openApiSpec.paths["/test"]?.valueOrNull()?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("api-key"))
        assertEquals(emptyList(), security[0]["api-key"])
    }

    @Test
    fun oAuth2SecurityAnnotations() = testApplication {
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
        @OptIn(ExperimentalKtorApi::class)
        routing {
            authenticate("oauth") {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants() +
                        call.application.findSecuritySchemes()
                )
            }.hide()
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()

        val openApiSpec = Json.decodeFromString<OpenApiDoc>(responseText)

        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")
        assertEquals(1, schemes.size)
        val oauthScheme = schemes["oauth"]?.valueOrNull() as OAuth2SecurityScheme
        assertNotNull(oauthScheme.flows?.authorizationCode)

        val operation = openApiSpec.paths["/test"]?.valueOrNull()?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("oauth"))
        assertEquals(setOf("profile", "email"), security[0]["oauth"]?.toSet())
    }

    @Test
    fun testDigestSecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            digest("digest-auth") {
                realm = "Digest Realm"
                digestProvider { _, _ -> null }
            }
        }
        openApiTestRoutes(authProvider = "digest-auth")

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()

        val openApiSpec = Json.decodeFromString<OpenApiDoc>(responseText)
        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")

        val scheme = schemes["digest-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("digest", scheme.scheme)
        assertEquals(null, scheme.bearerFormat)

        val operation = openApiSpec.paths["/test"]?.valueOrNull()?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("digest-auth"))
        assertEquals(emptyList(), security[0]["digest-auth"])
    }

    @Test
    fun testJWTSecurityAnnotations() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Authentication) {
            jwt("jwt-auth") {
                verifier(JWT.require(jwtAlgorithm).withAudience(jwtAudience).withIssuer(jwtIssuer).build())
                validate { _ -> null }
            }
        }
        openApiTestRoutes(authProvider = "jwt-auth")

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()

        val openApiSpec = Json.decodeFromString<OpenApiDoc>(responseText)
        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")

        val scheme = schemes["jwt-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)

        val operation = openApiSpec.paths["/test"]?.valueOrNull()?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("jwt-auth"))
        assertEquals(emptyList(), security[0]["jwt-auth"])
    }

    @Test
    fun `hide removes branch of routing tree`() = testApplication {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        @OptIn(ExperimentalKtorApi::class)
        routing {
            route("/hidden") {
                post("/foo") {
                    call.respond("hidden")
                }
                route("/sub") {
                    get {
                        call.respond("hidden")
                    }
                }
                get("/routes") {
                    call.respond(
                        OpenApiDoc(info = OpenApiInfo("Foo", "1.0.0")) +
                            call.application.routingRoot.descendants()
                    )
                }
            }.hide()

            route("/showing") {
                get("/messages") {
                    call.respond(listOf(Message.DM(1L, "Message", 128734L)))
                }
            }
        }

        val routesResponse = client.get("/hidden/routes")
        val responseText = routesResponse.bodyAsText()
        assertFalse("hidden" in responseText)
    }

    private fun TestApplicationBuilder.openApiTestRoutes(authProvider: String) {
        routing {
            authenticate(authProvider) {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            @OptIn(ExperimentalKtorApi::class)
            get("/routes") {
                call.respond(
                    OpenApiDoc(info = OpenApiInfo("Test API", "1.0.0")) +
                        call.application.routingRoot.descendants() +
                        call.application.findSecuritySchemes()
                )
            }.hide()
        }
    }
}

@Serializable
sealed interface Message {
    val id: Long
    val content: String
    val timestamp: Long

    @Serializable
    class DM(
        override val id: Long,
        override val content: String,
        override val timestamp: Long
    ) : Message

    @Serializable
    class Post(
        override val id: Long,
        override val content: String,
        override val timestamp: Long
    ) : Message
}
