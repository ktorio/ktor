/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.jwt

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*
import io.ktor.server.auth.jwt.typesafe.jwt as typedJwt

class TypedJwtAuthTest {

    @Test
    fun `jwt scheme authenticates and rejects`() = testApplication {
        val scheme = typedJwt<JwtUser>("typed-jwt") {
            verifier(ISSUER, AUDIENCE, ALGORITHM)
            validate { credential ->
                if (credential.audience.contains(AUDIENCE)) {
                    JwtUser(credential.payload.subject)
                } else {
                    null
                }
            }
        }

        routing {
            authenticateWith(scheme) {
                get("/profile") {
                    call.respondText(principal.name)
                }
            }
        }

        val ok = client.get("/profile") {
            header(HttpHeaders.Authorization, token(subject = "alice"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("alice", ok.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/profile").status)

        val invalid = client.get("/profile") {
            header(HttpHeaders.Authorization, token(audience = "wrong"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `jwt scheme accepts configured auth schemes`() = testApplication {
        val scheme = typedJwt<JwtUser>("typed-jwt-scheme") {
            authSchemes("Bearer", "Token")
            verifier(ISSUER, AUDIENCE, ALGORITHM)
            validate { credential -> JwtUser(credential.payload.subject) }
        }

        routing {
            authenticateWith(scheme) {
                get("/profile") {
                    call.respondText(principal.name)
                }
            }
        }

        val response = client.get("/profile") {
            header(HttpHeaders.Authorization, token(scheme = "Token", subject = "token-user"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("token-user", response.bodyAsText())
    }

    @Test
    fun `jwt scheme accepts configured auth header`() = testApplication {
        val scheme = typedJwt<JwtUser>("typed-jwt-header") {
            authHeader { call ->
                call.request.headers["X-Auth"]?.let { parseAuthorizationHeader(it) }
            }
            verifier(ISSUER, AUDIENCE, ALGORITHM)
            validate { credential -> JwtUser(credential.payload.subject) }
        }

        routing {
            authenticateWith(scheme) {
                get("/profile") {
                    call.respondText(principal.name)
                }
            }
        }

        val response = client.get("/profile") {
            header("X-Auth", token(subject = "header-user"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("header-user", response.bodyAsText())
    }

    @Test
    fun `jwt onUnauthorized can be configured per scheme and route`() = testApplication {
        val scheme = typedJwt<JwtUser>("typed-jwt-unauthorized") {
            onUnauthorized = { call, cause ->
                call.respondText("scheme:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
            }
            verifier(ISSUER, AUDIENCE, ALGORITHM)
            validate { credential -> JwtUser(credential.payload.subject) }
        }

        routing {
            authenticateWith(scheme) {
                get("/scheme") {
                    call.respondText(principal.name)
                }
            }
            authenticateWith(
                scheme,
                onUnauthorized = { call, cause ->
                    call.respondText("route:${cause::class.simpleName}", status = HttpStatusCode.Unauthorized)
                }
            ) {
                get("/route") {
                    call.respondText(principal.name)
                }
            }
        }

        val schemeResponse = client.get("/scheme")
        assertEquals(HttpStatusCode.Unauthorized, schemeResponse.status)
        assertEquals("scheme:NoCredentials", schemeResponse.bodyAsText())

        val routeResponse = client.get("/route") {
            header(HttpHeaders.Authorization, token(audience = "wrong"))
        }
        assertEquals(HttpStatusCode.Unauthorized, routeResponse.status)
        assertEquals("route:InvalidCredentials", routeResponse.bodyAsText())
    }

    private data class JwtUser(val name: String)

    private fun token(
        scheme: String = "Bearer",
        subject: String = "user",
        audience: String = AUDIENCE
    ): String = "$scheme " + JWT.create()
        .withAudience(audience)
        .withIssuer(ISSUER)
        .withSubject(subject)
        .sign(ALGORITHM)

    private companion object {
        private const val ISSUER = "https://jwt-provider-domain/"
        private const val AUDIENCE = "jwt-audience"
        private val ALGORITHM = Algorithm.HMAC256("secret")
    }
}
