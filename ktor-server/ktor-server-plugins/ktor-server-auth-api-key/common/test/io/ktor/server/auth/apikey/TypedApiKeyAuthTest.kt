/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.apikey

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*
import io.ktor.server.auth.apikey.typesafe.apiKey as typedApiKey

class TypedApiKeyAuthTest {

    @Test
    fun `api key scheme authenticates and rejects`() = testApplication {
        val scheme = typedApiKey<ApiKeyPrincipal>("typed-api-key") {
            validate { apiKey ->
                if (apiKey == "valid") ApiKeyPrincipal(apiKey) else null
            }
        }

        routing {
            authenticateWith(
                scheme,
                onUnauthorized = { call, cause ->
                    call.respondText(cause::class.simpleName!!, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/protected") {
                    call.respondText(principal.key)
                }
            }
        }

        val ok = client.get("/protected") {
            header(ApiKeyAuth.DEFAULT_HEADER_NAME, "valid")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("valid", ok.bodyAsText())

        val missing = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("NoCredentials", missing.bodyAsText())

        val invalid = client.get("/protected") {
            header(ApiKeyAuth.DEFAULT_HEADER_NAME, "invalid")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertEquals("InvalidCredentials", invalid.bodyAsText())
    }

    @Test
    fun `api key scheme accepts configured header`() = testApplication {
        val scheme = typedApiKey<ApiKeyPrincipal>("typed-api-key-header") {
            headerName = "X-Custom-Api-Key"
            validate { apiKey ->
                if (apiKey == "custom") ApiKeyPrincipal(apiKey) else null
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/protected") {
                    call.respondText(principal.key)
                }
            }
        }

        val response = client.get("/protected") {
            header("X-Custom-Api-Key", "custom")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("custom", response.bodyAsText())
    }

    @Test
    fun `api key onUnauthorized can be configured per scheme and route`() = testApplication {
        val scheme = typedApiKey<ApiKeyPrincipal>("typed-api-key-unauthorized") {
            onUnauthorized = { call, cause ->
                call.respondText("scheme:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
            }
            validate { apiKey ->
                if (apiKey == "valid") ApiKeyPrincipal(apiKey) else null
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/scheme") {
                    call.respondText(principal.key)
                }
            }
            authenticateWith(
                scheme,
                onUnauthorized = { call, cause ->
                    call.respondText("route:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/route") {
                    call.respondText(principal.key)
                }
            }
        }

        val schemeResponse = client.get("/scheme")
        assertEquals(HttpStatusCode.Unauthorized, schemeResponse.status)
        assertEquals("scheme:NoCredentials", schemeResponse.bodyAsText())

        val routeResponse = client.get("/route") {
            header(ApiKeyAuth.DEFAULT_HEADER_NAME, "invalid")
        }
        assertEquals(HttpStatusCode.Unauthorized, routeResponse.status)
        assertEquals("route:InvalidCredentials", routeResponse.bodyAsText())
    }

    @Test
    fun `api key any-of failures are tracked per typed scheme name`() = testApplication {
        val primary = typedApiKey<ApiKeyPrincipal>("primary-api-key") {
            headerName = "X-Primary-Api-Key"
            validate { apiKey ->
                if (apiKey == "primary") ApiKeyPrincipal(apiKey) else null
            }
        }
        val secondary = typedApiKey<ApiKeyPrincipal>("secondary-api-key") {
            headerName = "X-Secondary-Api-Key"
            validate { apiKey ->
                if (apiKey == "secondary") ApiKeyPrincipal(apiKey) else null
            }
        }

        routing {
            authenticateWithAnyOf(
                primary,
                secondary,
                onUnauthorized = { call, failures ->
                    val text = failures.entries.joinToString(";") { (name, cause) ->
                        "$name=${cause::class.simpleName}"
                    }
                    call.respondText(text, status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/protected") {
                    call.respondText(principal.key)
                }
            }
        }

        val rejected = client.get("/protected") {
            header("X-Secondary-Api-Key", "wrong")
        }
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
        assertEquals("primary-api-key=NoCredentials;secondary-api-key=InvalidCredentials", rejected.bodyAsText())

        val accepted = client.get("/protected") {
            header("X-Secondary-Api-Key", "secondary")
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals("secondary", accepted.bodyAsText())
    }

    private data class ApiKeyPrincipal(val key: String)
}
