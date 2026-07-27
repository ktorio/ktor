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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.reflect.typeOf
import kotlin.test.*

class DescribeRouteTest {

    private val jwtSecret = "test-jwt-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    val testMessage = Message.DM(1L, "Hello, world!", 16777216000)

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

    @Test
    fun `same serial name across sealed hierarchies keeps distinct component schemas`() = testApplication {
        val firstSchema = KotlinxJsonSchemaInference.jsonSchema<FirstResponse>()
        val secondSchema = KotlinxJsonSchemaInference.jsonSchema<SecondResponse>()
        assertEquals(
            listOf(componentName<FirstResponse.SharedCase>()),
            firstSchema.oneOf?.mapNotNull { it.valueOrNull()?.title },
        )
        assertEquals(
            listOf(componentName<SecondResponse.SharedCase>()),
            secondSchema.oneOf?.mapNotNull { it.valueOrNull()?.title },
        )

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

            get("/first") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<FirstResponse>()
                    }
                }
            }

            get("/second") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<SecondResponse>()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas
        assertNotNull(schemas, "Schema components should be defined")

        assertFalse("shared_case" in schemas, "SerialName should not be used as a component key: ${schemas.keys}")

        val sharedCaseSchemas = schemas.filterKeys { it == "SharedCase" || it.endsWith(".SharedCase") }
        assertEquals(2, sharedCaseSchemas.size, "Expected two distinct SharedCase schemas, but found: ${schemas.keys}")
        assertTrue(
            sharedCaseSchemas.values.any { "value" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'value', but found: $sharedCaseSchemas"
        )
        assertTrue(
            sharedCaseSchemas.values.any { "count" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'count', but found: $sharedCaseSchemas"
        )
    }

    @Test
    fun `same serial name across nested sealed hierarchies keeps distinct component schemas`() = testApplication {
        val nestedSchema = KotlinxJsonSchemaInference.jsonSchema<NestedResponses>()
        val nestedProperties = nestedSchema.properties ?: fail("NestedResponses should expose properties")
        assertEquals(
            componentName<FirstResponse>(),
            nestedProperties["first"]?.valueOrNull()?.title
                ?: (nestedProperties["first"] as? ReferenceOr.Reference)?.ref?.substringAfterLast('/'),
        )
        assertEquals(
            componentName<SecondResponse>(),
            nestedProperties["second"]?.valueOrNull()?.title
                ?: (nestedProperties["second"] as? ReferenceOr.Reference)?.ref?.substringAfterLast('/'),
        )

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

            get("/nested") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<NestedResponses>()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas
        assertNotNull(schemas, "Schema components should be defined")

        assertFalse("shared_case" in schemas, "SerialName should not be used as a component key: ${schemas.keys}")

        val nestedComponent = schemas["NestedResponses"] ?: fail("NestedResponses schema should be present")
        val properties = nestedComponent.properties ?: fail("NestedResponses schema should have properties")
        assertEquals(
            "#/components/schemas/FirstResponse",
            (properties["first"] as? ReferenceOr.Reference)?.ref,
        )
        assertEquals(
            "#/components/schemas/SecondResponse",
            (properties["second"] as? ReferenceOr.Reference)?.ref,
        )

        val firstRootSchema = schemas["FirstResponse"] ?: fail("FirstResponse schema should be present")
        val secondRootSchema = schemas["SecondResponse"] ?: fail("SecondResponse schema should be present")
        assertNotNull(firstRootSchema.oneOf, "FirstResponse should remain polymorphic")
        assertNotNull(secondRootSchema.oneOf, "SecondResponse should remain polymorphic")

        val sharedCaseSchemas = schemas.filterKeys { it == "SharedCase" || it.endsWith(".SharedCase") }
        assertEquals(2, sharedCaseSchemas.size, "Expected two distinct SharedCase schemas, but found: ${schemas.keys}")
        assertTrue(
            sharedCaseSchemas.values.any { "value" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'value', but found: $sharedCaseSchemas"
        )
        assertTrue(
            sharedCaseSchemas.values.any { "count" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'count', but found: $sharedCaseSchemas"
        )
    }

    @Test
    fun `nullable sealed schema generation keeps distinct subtype component names`() {
        val nullableSchema = KotlinxJsonSchemaInference.buildSchema(typeOf<FirstResponse?>())
        assertEquals(
            listOf(componentName<FirstResponse.SharedCase>()),
            nullableSchema.oneOf?.mapNotNull { it.valueOrNull()?.title },
        )
    }

    @Test
    fun `nullable sealed root preserves nested sealed subtype naming`() {
        val nullableSchema = KotlinxJsonSchemaInference.buildSchema(typeOf<NullableNestedRoot?>())
        val subtypeSchemas = nullableSchema.oneOf?.mapNotNull { it.valueOrNull() } ?: fail("Expected sealed subtypes")

        val firstNestedSchema = subtypeSchemas
            .firstOrNull { it.title == componentName<NullableNestedRoot.First>() }
            ?.properties?.get("nested")?.valueOrNull()
            ?: fail("Expected nested schema for first subtype")
        val secondNestedSchema = subtypeSchemas
            .firstOrNull { it.title == componentName<NullableNestedRoot.Second>() }
            ?.properties?.get("nested")?.valueOrNull()
            ?: fail("Expected nested schema for second subtype")

        assertEquals(
            listOf(componentName<FirstNested.SharedCase>()),
            firstNestedSchema.oneOf?.mapNotNull { it.valueOrNull()?.title },
            "Unexpected nested schema for first subtype: $firstNestedSchema",
        )
        assertEquals(
            listOf(componentName<SecondNested.SharedCase>()),
            secondNestedSchema.oneOf?.mapNotNull { it.valueOrNull()?.title },
            "Unexpected nested schema for second subtype: $secondNestedSchema",
        )
    }

    @Test
    fun `recursive sealed subtype references renamed component`() {
        val schema = KotlinxJsonSchemaInference.jsonSchema<RecursiveResponse>()
        val recursiveSubtype = schema.oneOf?.singleOrNull()?.valueOrNull() ?: fail("Expected recursive subtype")
        val nextSchema =
            recursiveSubtype.properties?.get("next")?.valueOrNull() ?: fail("Expected nullable next schema")
        assertEquals(
            "#/components/schemas/${componentName<RecursiveResponse.Node>()}",
            (nextSchema.oneOf?.firstOrNull() as? ReferenceOr.Reference)?.ref,
            "Unexpected recursive subtype schema: $recursiveSubtype",
        )
    }

    @Test
    fun `recursive sealed subtype references shortened component in final openapi`() = testApplication {
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

            get("/recursive") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<RecursiveResponse>()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas ?: fail("Schema components should be defined")

        val nodeSchema = schemas["Node"] ?: fail("Node schema should be present: ${schemas.keys}")
        val nextSchema = nodeSchema.properties?.get("next")?.valueOrNull() ?: fail("Expected nullable next schema")
        assertEquals(
            "#/components/schemas/Node",
            (nextSchema.oneOf?.firstOrNull() as? ReferenceOr.Reference)?.ref,
            "Unexpected recursive subtype schema in final OpenAPI: $nodeSchema",
        )
    }

    @Test
    fun `recursive sealed subtype in list references shortened component in final openapi`() = testApplication {
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

            get("/recursive-list") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<RecursiveListResponse>()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas ?: fail("Schema components should be defined")

        val nodeSchema = schemas["Node"] ?: fail("Node schema should be present: ${schemas.keys}")
        val childrenSchema = nodeSchema.properties?.get("children")?.valueOrNull() ?: fail("Expected children schema")
        assertEquals(
            "#/components/schemas/Node",
            (childrenSchema.items as? ReferenceOr.Reference)?.ref,
            "Unexpected recursive list subtype schema in final OpenAPI: $nodeSchema",
        )
    }

    @Test
    fun `annotation item refs keep distinct sealed subtype component names`() = testApplication {
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

            get("/annotated-lists") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = jsonSchema<AnnotatedSharedCaseLists>()
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas ?: fail("Schema components should be defined")

        val annotatedSchema =
            schemas["AnnotatedSharedCaseLists"] ?: fail("AnnotatedSharedCaseLists schema should be present")
        val properties = annotatedSchema.properties ?: fail("AnnotatedSharedCaseLists should have properties")
        val firstSchema = properties["first"]?.valueOrNull() ?: fail("Expected first schema")
        val secondSchema = properties["second"]?.valueOrNull() ?: fail("Expected second schema")
        val firstRef = (firstSchema.items as? ReferenceOr.Reference)?.ref ?: fail("Expected first items ref")
        val secondRef = (secondSchema.items as? ReferenceOr.Reference)?.ref ?: fail("Expected second items ref")
        assertNotEquals(firstRef, secondRef, "Annotated item refs should point to distinct components")

        val sharedCaseSchemas = schemas.filterKeys { it == "SharedCase" || it.endsWith(".SharedCase") }
        assertEquals(2, sharedCaseSchemas.size, "Expected two distinct SharedCase schemas, but found: ${schemas.keys}")
        assertTrue(
            sharedCaseSchemas.values.any { "value" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'value', but found: $sharedCaseSchemas",
        )
        assertTrue(
            sharedCaseSchemas.values.any { "count" in (it.properties ?: emptyMap()) },
            "Expected one SharedCase schema with property 'count', but found: $sharedCaseSchemas",
        )
    }

    @Test
    fun `discriminator mappings keep external refs during component rewrite`() = testApplication {
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

            get("/external-mapping") {
                call.respondText("ok")
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        schema = JsonSchema(
                            title = "ExternalDiscriminator",
                            discriminator = JsonSchemaDiscriminator(
                                propertyName = "kind",
                                mapping = mapOf("external" to "https://example.com/schemas/shared_case")
                            )
                        )
                    }
                }
            }
        }

        val routesResponse = client.get("/routes")
        val responseText = routesResponse.bodyAsText()
        val openApiSpec = jsonFormat.decodeFromString<OpenApiDoc>(responseText)
        val schemas = openApiSpec.components?.schemas ?: fail("Schema components should be defined")

        val schema = schemas["ExternalDiscriminator"] ?: fail("ExternalDiscriminator schema should be present")
        assertEquals(
            "https://example.com/schemas/shared_case",
            schema.discriminator?.mapping?.get("external"),
        )
    }

    private inline fun <reified T : Any> componentName(): String =
        T::class.qualifiedName ?: fail("Missing qualified name")

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

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("status")
@Serializable
sealed interface FirstResponse {
    @Serializable
    @SerialName("shared_case")
    data class SharedCase(val value: String) : FirstResponse
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("status")
@Serializable
sealed interface SecondResponse {
    @Serializable
    @SerialName("shared_case")
    data class SharedCase(val count: Int) : SecondResponse
}

@Serializable
data class NestedResponses(
    val first: FirstResponse,
    val second: SecondResponse,
)

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("status")
@Serializable
sealed interface NullableNestedRoot {
    @Serializable
    @SerialName("first")
    data class First(val nested: FirstNested) : NullableNestedRoot

    @Serializable
    @SerialName("second")
    data class Second(val nested: SecondNested) : NullableNestedRoot
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed interface FirstNested {
    @Serializable
    @SerialName("shared_case")
    data class SharedCase(val value: String) : FirstNested
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed interface SecondNested {
    @Serializable
    @SerialName("shared_case")
    data class SharedCase(val count: Int) : SecondNested
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("status")
@Serializable
sealed interface RecursiveResponse {
    @Serializable
    @SerialName("node")
    data class Node(val next: Node?) : RecursiveResponse
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("status")
@Serializable
sealed interface RecursiveListResponse {
    @Serializable
    @SerialName("node")
    data class Node(val children: List<Node>) : RecursiveListResponse
}

@Serializable
data class AnnotatedSharedCaseLists(
    @JsonSchema.ItemsRef(FirstNested.SharedCase::class)
    val first: List<String>,
    @JsonSchema.ItemsRef(SecondNested.SharedCase::class)
    val second: List<String>,
)
