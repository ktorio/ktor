/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RateLimitIntegrationTest {

    @Test
    fun rateLimitRunsBeforeMockAuthWhenRateLimitWrapsAuthenticate() = testApplication {
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
    fun rateLimitRunsAfterMockAuthWhenAuthenticateWrapsRateLimit() = testApplication {
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
    fun rateLimitUsesPrincipalWhenAuthenticateWrapsRateLimit() = testApplication {
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
    fun rateLimitRunsBeforeAuthWhenRateLimitWrapsAuthenticate() = testApplication {
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
}
