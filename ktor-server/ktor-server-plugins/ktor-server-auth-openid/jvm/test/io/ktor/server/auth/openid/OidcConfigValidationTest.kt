/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcConfigValidationTest {

    @Test
    fun `route uri configs reject query parameters`() {
        val failure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                loginUri {
                    path("auth0", "login")
                    parameters.append("debug", "true")
                }
            }
        }

        assertContains(failure.message.orEmpty(), "query parameters")
    }

    @Test
    fun `bearer and oauth configs validate required security settings`() {
        val bearerFailure = assertProviderValidationFails { bearer() }
        assertContains(bearerFailure.message.orEmpty(), "accessToken")
        assertContains(bearerFailure.message.orEmpty(), "audiences")

        val scopeFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                scopes = listOf("profile")
            }
        }
        assertContains(scopeFailure.message.orEmpty(), "openid")
        assertContains(scopeFailure.message.orEmpty(), "accessToken")

        val audienceFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                idTokenAudience = " "
            }
        }
        assertContains(audienceFailure.message.orEmpty(), "idTokenAudience")
    }

    @Test
    fun `jwkProviderFactory cannot be combined with cache or rate-limit config`() {
        val configurations: List<Pair<String, OidcJwtConfig.() -> Unit>> = listOf(
            "jwkCache" to { jwkCache() },
            "disableJwkCache" to { disableJwkCache() },
            "jwkRateLimit" to { jwkRateLimit() },
            "disableJwkRateLimit" to { disableJwkRateLimit() },
        )

        configurations.forEach { (name, configureJwt) ->
            val failure = assertProviderValidationFails {
                jwt {
                    jwkProviderFactory = { error("not used") }
                    configureJwt()
                }
            }
            assertContains(failure.message.orEmpty(), "jwkProviderFactory", message = name)
            assertContains(failure.message.orEmpty(), "jwkCache or jwkRateLimit", message = name)
        }
    }

    @Test
    fun `bearer token source defaults to authorization header unless customized`() {
        OidcProviderConfig("default", OidcPrincipal::class).apply {
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
        }
        OidcProviderConfig("custom", OidcPrincipal::class).apply {
            bearer {
                tokenExtractor = { call -> call.request.headers["X-Token"] }
            }
            assertNotNull(bearerConfig!!.tokenExtractor)
        }
    }

    private fun assertProviderValidationFails(
        configure: OidcProviderConfig<OidcPrincipal>.() -> Unit,
    ): Throwable = assertFailsWith<IllegalArgumentException> {
        testApplication {
            openIdProvider()
            val openIdClient = client
            application {
                val oidc = openIdConnect {
                    httpClient = openIdClient
                    discoveryRefreshInterval = ZERO
                }
                oidc.provider("auth0") {
                    issuer = ISSUER_URL
                    configure()
                }
            }
            startApplication()
        }
    }
}
