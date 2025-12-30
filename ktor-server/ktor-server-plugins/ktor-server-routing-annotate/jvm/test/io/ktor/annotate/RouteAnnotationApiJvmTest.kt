/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RouteAnnotationApiJvmTest {

    private val jwtSecret = "test-jwt-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    @OptIn(ExperimentalSerializationApi::class)
    val jsonFormat = Json {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun TestApplicationBuilder.openApiTestRoutes(authProvider: String) {
        routing {
            authenticate(authProvider) {
                get("/test") {
                    call.respond("authenticated")
                }
            }
            get("/routes") {
                call.respond(
                    generateOpenApiSpec(
                        info = OpenApiInfo("Test API", "1.0.0"),
                        route = call.application.routingRoot
                    )
                )
            }
        }
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

        val openApiSpec = Json.decodeFromString<OpenApiSpecification>(responseText)
        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")

        val scheme = schemes["digest-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("digest", scheme.scheme)
        assertEquals(null, scheme.bearerFormat)

        val operation = openApiSpec.paths["/test"]?.get
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

        val openApiSpec = Json.decodeFromString<OpenApiSpecification>(responseText)
        // Ensure a security scheme is serialized/deserialized correctly
        val schemes = openApiSpec.components?.securitySchemes
        assertNotNull(schemes, "Security schemes should be defined")

        val scheme = schemes["jwt-auth"]?.valueOrNull() as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("bearer", scheme.scheme)
        assertEquals("JWT", scheme.bearerFormat)

        val operation = openApiSpec.paths["/test"]?.get
        val security = operation?.security!!
        assertEquals(1, security.size)
        assertEquals(true, security[0].containsKey("jwt-auth"))
        assertEquals(emptyList(), security[0]["jwt-auth"])
    }
}
