/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.plugins.ratelimit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RateLimitIntegrationTest {

    private data class ApiKeyPrincipal(val key: String)
    private data class User(val name: String)

    @Test
    fun `rate limit runs before authentication when rate limit wraps authenticate`() = testApplication {
        install(RateLimit) {
            register(RateLimitName("block-all")) {
                rateLimiter(limit = 0, refillPeriod = 1.seconds)
            }
        }
        install(Authentication) {
            basic("auth") {
                validate { UserIdPrincipal(it.name) }
            }
        }

        routing {
            rateLimit(RateLimitName("block-all")) {
                authenticate("auth") {
                    post("/endpoint") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        assertEquals(HttpStatusCode.TooManyRequests, client.post("/endpoint").status)
    }

    @Test
    fun `rate limit runs after authentication when authenticate wraps rate limit`() = testApplication {
        install(RateLimit) {
            register(RateLimitName("block-all")) {
                rateLimiter(limit = 0, refillPeriod = 1.seconds)
            }
        }
        install(Authentication) {
            basic("auth") {
                validate { UserIdPrincipal(it.name) }
            }
        }

        routing {
            authenticate("auth") {
                rateLimit(RateLimitName("block-all")) {
                    post("/endpoint") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.post("/endpoint").status)
    }

    @Test
    fun `rate limit uses principal when authenticate wraps rate limit`() = testApplication {
        install(RateLimit) {
            register(RateLimitName("MyRateLimit")) {
                rateLimiter(limit = 10, refillPeriod = 1.seconds)
                requestKey { call ->
                    call.principal<UserIdPrincipal>()?.name ?: error("principal is not available")
                }
            }
        }
        install(Authentication) {
            basic("auth") {
                validate { UserIdPrincipal(it.name) }
            }
        }

        routing {
            authenticate("auth") {
                rateLimit(RateLimitName("MyRateLimit")) {
                    post("/endpoint") {
                        call.respondText(call.principal<UserIdPrincipal>()?.name ?: "unknown")
                    }
                }
            }
        }

        val response = client.post("/endpoint") {
            basicAuth("user", "password")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user", response.bodyAsText())
    }

    @Test
    fun `rate limit runs before auth when rate limit wraps authenticate`() = testApplication {
        install(RateLimit) {
            register(RateLimitName("block-all")) {
                rateLimiter(limit = 0, refillPeriod = 1.seconds)
            }
        }
        install(Authentication) {
            basic("auth") {
                validate { UserIdPrincipal(it.name) }
            }
        }

        routing {
            rateLimit(RateLimitName("block-all")) {
                authenticate("auth") {
                    post("/endpoint") {
                        call.respondText(call.principal<UserIdPrincipal>()?.name ?: "unknown")
                    }
                }
            }
        }

        val status = client.post("/endpoint") { basicAuth("user", "password") }.status
        assertEquals(HttpStatusCode.TooManyRequests, status)
    }

    @Test
    fun `rate limit uses principal from outer authenticate for nested authenticate route`() = testApplication {
        val rateLimitName = RateLimitName("multi-auth")

        install(Authentication) {
            bearer("bearer") {
                authenticate { credential ->
                    UserIdPrincipal("user").takeIf { credential.token == "valid-token" }
                }
            }

            apiKey("apiKey") {
                headerName = "X-Secret-Key"
                validate { keyFromHeader ->
                    ApiKeyPrincipal(keyFromHeader).takeIf { keyFromHeader == "valid-api-key" }
                }
            }
        }

        install(RateLimit) {
            register(rateLimitName) {
                rateLimiter(limit = 120, refillPeriod = 1.minutes)
                requestKey { call ->
                    call.principal<UserIdPrincipal>()?.name
                        ?: error(
                            "No bearer principal found. " +
                                "Api key principal available: ${call.principal<ApiKeyPrincipal>() != null}"
                        )
                }
            }
        }

        routing {
            authenticate("bearer", strategy = AuthenticationStrategy.Required) {
                rateLimit(rateLimitName) {
                    get("/bearerOnly") {
                        call.respond(HttpStatusCode.OK)
                    }

                    authenticate("apiKey", strategy = AuthenticationStrategy.Required) {
                        get("/bearerAndApiKey") {
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }
            }
        }

        val bearerOnly = client.get("/bearerOnly") {
            bearerAuth("valid-token")
        }
        assertEquals(HttpStatusCode.OK, bearerOnly.status)

        val bearerAndApiKey = client.get("/bearerAndApiKey") {
            header("X-Secret-Key", "valid-api-key")
            bearerAuth("valid-token")
        }
        assertEquals(HttpStatusCode.OK, bearerAndApiKey.status)

        val missingApiKey = client.get("/bearerAndApiKey") {
            bearerAuth("valid-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, missingApiKey.status)

        val missingBearer = client.get("/bearerAndApiKey") {
            header("X-Secret-Key", "valid-api-key")
        }
        assertEquals(HttpStatusCode.Unauthorized, missingBearer.status)
    }

    @Test
    fun `rate limit runs before typed authenticateWith when rate limit wraps it`() = testApplication {
        val rateLimitName = RateLimitName("block-all")
        install(RateLimit) {
            register(rateLimitName) {
                rateLimiter(limit = 0, refillPeriod = 1.seconds)
            }
        }
        val userAuth = bearer<User>("typed-bearer-wrapped") {
            validate { token -> User(token.token).takeIf { token.token == "valid-token" } }
        }

        routing {
            rateLimit(rateLimitName) {
                authenticateWith(userAuth) {
                    get("/endpoint") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        val response = client.get("/endpoint") { bearerAuth("valid-token") }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `typed authenticateWith runs before rate limit when it wraps rate limit`() = testApplication {
        val rateLimitName = RateLimitName("block-all")
        install(RateLimit) {
            register(rateLimitName) {
                rateLimiter(limit = 0, refillPeriod = 1.seconds)
            }
        }
        val userAuth = bearer<User>("typed-bearer-wrapping") {
            validate { token -> User(token.token).takeIf { token.token == "valid-token" } }
        }

        routing {
            authenticateWith(userAuth) {
                rateLimit(rateLimitName) {
                    get("/endpoint") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        val response = client.get("/endpoint")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `rate limit requestKey reads the principal set by typed authenticateWith`() = testApplication {
        val rateLimitName = RateLimitName("per-user")
        val userAuth = bearer<User>("typed-bearer-request-key") {
            validate { token -> User(token.token).takeIf { token.token == "valid-token" } }
        }
        install(RateLimit) {
            register(rateLimitName) {
                rateLimiter(limit = 10, refillPeriod = 1.minutes)
                requestKey { call -> call.principal<User>()?.name ?: error("principal is not available") }
            }
        }

        routing {
            authenticateWith(userAuth) {
                rateLimit(rateLimitName) {
                    get("/endpoint") {
                        call.respondText(call.principal.name)
                    }
                }
            }
        }

        val response = client.get("/endpoint") { bearerAuth("valid-token") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("valid-token", response.bodyAsText())
    }

    @Test
    fun `rate limit uses principal from outer typed authenticateWith for nested authenticateWith route`() =
        testApplication {
            val rateLimitName = RateLimitName("nested-typed-auth")
            val outerAuth = bearer<User>("typed-outer") {
                validate { token -> User(token.token).takeIf { token.token == "valid-token" } }
            }
            val innerAuth = apiKey<ApiKeyPrincipal>("typed-inner") {
                headerName = "X-Secret-Key"
                validate { key -> ApiKeyPrincipal(key).takeIf { key == "valid-secret" } }
            }

            install(RateLimit) {
                register(rateLimitName) {
                    rateLimiter(limit = 120, refillPeriod = 1.minutes)
                    requestKey { call ->
                        call.principal<User>()?.name
                            ?: error(
                                "No bearer principal found. " +
                                    "Api key principal available: ${call.principal<ApiKeyPrincipal>() != null}"
                            )
                    }
                }
            }

            routing {
                authenticateWith(outerAuth) {
                    rateLimit(rateLimitName) {
                        get("/outerOnly") {
                            call.respondText(call.principal.name)
                        }

                        authenticateWith(innerAuth) {
                            get("/outerAndInner") {
                                call.respondText(call.principal.key)
                            }
                        }
                    }
                }
            }

            val outerOnly = client.get("/outerOnly") { bearerAuth("valid-token") }
            assertEquals(HttpStatusCode.OK, outerOnly.status)
            assertEquals("valid-token", outerOnly.bodyAsText())

            val outerAndInner = client.get("/outerAndInner") {
                bearerAuth("valid-token")
                header("X-Secret-Key", "valid-secret")
            }
            assertEquals(HttpStatusCode.OK, outerAndInner.status)
            assertEquals("valid-secret", outerAndInner.bodyAsText())
        }
}
