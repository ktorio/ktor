/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*

class ProtectedResourceMetadataTest {

    @Test
    fun `protected resource metadata endpoint returns correct JSON`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {
                    resourceName = "My API"
                }
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val response = client.get("/.well-known/oauth-protected-resource")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = response.bodyAsText()
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(body)
        assertEquals("https://api.example.com", metadata.resource)
        assertEquals(listOf(ISSUER_URL), metadata.authorizationServers)
        assertEquals("My API", metadata.resourceName)
    }

    @Test
    fun `protected resource metadata can be enabled with defaults`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com")
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(
            client.get("/.well-known/oauth-protected-resource").bodyAsText()
        )
        assertEquals("https://api.example.com", metadata.resource)
        assertEquals(listOf("header"), metadata.bearerMethodsSupported)
    }

    @Test
    fun `protected resource metadata supports explicit scopes`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {
                    scopesSupported = listOf("openid", "profile", "custom-scope")
                }
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val response = client.get("/.well-known/oauth-protected-resource")
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(response.bodyAsText())
        assertEquals(listOf("openid", "profile", "custom-scope"), metadata.scopesSupported)
    }

    @Test
    fun `protected resource metadata explicit overrides take precedence`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {
                    authorizationServers = listOf("https://custom-as.example.com")
                    scopesSupported = listOf("read", "write")
                }
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val response = client.get("/.well-known/oauth-protected-resource")
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(response.bodyAsText())
        assertEquals(listOf("https://custom-as.example.com"), metadata.authorizationServers)
        assertEquals(listOf("read", "write"), metadata.scopesSupported)
    }

    @Test
    fun `protected resource metadata disabled by default`() = testApplication {
        application {
            val oidc = openIdConnect { }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val response = client.get("/.well-known/oauth-protected-resource")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `WWW-Authenticate includes resource_metadata when protected resource configured`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {}
            }
            val provider = oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("my-api")
                }
                bearer()
            }

            routing {
                authenticateWith(provider.bearer) {
                    get("/protected") { call.respondText("ok") }
                }
            }
        }
        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate]
        assertNotNull(wwwAuth)
        assertContains(wwwAuth, "resource_metadata=")
        assertContains(wwwAuth, "https://api.example.com/.well-known/oauth-protected-resource")
    }

    @Test
    fun `WWW-Authenticate omits resource_metadata when protected resource not configured`() = testApplication {
        application {
            val oidc = openIdConnect { }
            val provider = oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("my-api")
                }
                bearer()
            }
            routing {
                authenticateWith(provider.bearer) {
                    get("/protected") { call.respondText("ok") }
                }
            }
        }
        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate] ?: ""
        assertFalse(wwwAuth.contains("resource_metadata"))
    }

    @Test
    fun `protected resource metadata routes follow resource path and port`() {
        val cases = listOf(
            "https://api.example.com" to "/.well-known/oauth-protected-resource",
            "https://api.example.com/v1/" to "/.well-known/oauth-protected-resource/v1",
            "https://api.example.com/v1" to "/.well-known/oauth-protected-resource/v1",
            "https://api.example.com:8443/v1" to "/.well-known/oauth-protected-resource/v1",
        )

        cases.forEach { (resource, routePath) ->
            testApplication {
                configureProtectedResourceApplication(resource, protectedPath = "/protected")

                val response = client.get(routePath)
                assertEquals(HttpStatusCode.OK, response.status, resource)
                val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(response.bodyAsText())
                assertEquals(resource, metadata.resource)

                val expectedMetadataUrl = buildResourceMetadataUrl(resource)
                assertContains(expectedMetadataUrl, routePath)
                val wwwAuth = client.get("/protected").headers[HttpHeaders.WWWAuthenticate]
                assertNotNull(wwwAuth)
                assertContains(wwwAuth, expectedMetadataUrl)

                if (routePath != "/.well-known/oauth-protected-resource") {
                    assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/oauth-protected-resource").status)
                }
            }
        }
    }

    @Test
    fun `protected resource metadata omits null fields`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {}
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val body = client.get("/.well-known/oauth-protected-resource").bodyAsText()
        assertFalse(body.contains("\"jwks_uri\""))
        assertFalse(body.contains("\"resource_name\""))
        assertFalse(body.contains("\"dpop_bound_access_tokens_required\""))
        assertContains(body, "\"resource\"")
        assertContains(body, "\"authorization_servers\"")
    }

    @Test
    fun `protected resource metadata does not infer custom bearer extractors`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {}
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer {
                    tokenExtractor = { call -> call.request.headers["X-Token"] }
                }
            }
        }
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(
            client.get("/.well-known/oauth-protected-resource").bodyAsText()
        )
        assertNull(metadata.bearerMethodsSupported)
    }

    @Test
    fun `protected resource metadata uses explicit bearer methods with custom extractor`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {
                    bearerMethodsSupported = listOf("body")
                }
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer {
                    tokenExtractor = { call -> call.request.headers["X-Token"] }
                }
            }
        }
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(
            client.get("/.well-known/oauth-protected-resource").bodyAsText()
        )
        assertEquals(listOf("body"), metadata.bearerMethodsSupported)
    }

    @Test
    fun `protected resource metadata derives bearer methods from token sources`() = testApplication {
        application {
            val oidc = openIdConnect {
                protectedResource("https://api.example.com") {}
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
        }
        val metadata = discoveryJson.decodeFromString<ProtectedResourceMetadata>(
            client.get("/.well-known/oauth-protected-resource").bodyAsText()
        )
        assertEquals(listOf("header"), metadata.bearerMethodsSupported)
    }

    @Test
    fun `protected resource metadata rejects unsupported resource URLs`() {
        val invalidResources = listOf(
            "https://user@api.example.com" to "userinfo",
            "https://api.example.com?foo=bar" to "query",
            "https://api.example.com#metadata" to "fragment",
            "http://api.example.com" to "https",
            "https:///v1" to "host",
        )

        invalidResources.forEach { (resource, expectedMessage) ->
            val failure = assertFailsWith<IllegalArgumentException> {
                testApplication {
                    application {
                        openIdConnect {
                            protectedResource(resource)
                        }
                    }
                    startApplication()
                }
            }
            assertContains(failure.message.orEmpty(), "protectedResource(resource)")
            assertContains(failure.message.orEmpty(), expectedMessage)
        }
    }

    private suspend fun ApplicationTestBuilder.configureProtectedResourceApplication(
        resource: String,
        protectedPath: String? = null,
    ) {
        application {
            val oidc = openIdConnect {
                protectedResource(resource) {}
            }
            val provider = oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            if (protectedPath != null) {
                routing {
                    authenticateWith(provider.bearer) {
                        get(protectedPath) { call.respondText("ok") }
                    }
                }
            }
        }
    }
}
