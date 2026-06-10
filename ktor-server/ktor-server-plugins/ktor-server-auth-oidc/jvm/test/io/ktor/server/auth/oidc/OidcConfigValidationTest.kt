/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcConfigValidationTest {

    @Test
    fun `bearer config validates required access token settings`() {
        val bearerFailure = assertProviderValidationFails { bearer() }
        assertContains(bearerFailure.message.orEmpty(), "accessToken")
        assertContains(bearerFailure.message.orEmpty(), "audiences")
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
        OidcProviderConfig("default", OidcToken::class).apply {
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
        }
        OidcProviderConfig("custom", OidcToken::class).apply {
            bearer {
                tokenExtractor = { call -> call.request.headers["X-Token"] }
            }
            assertNotNull(bearerConfig!!.tokenExtractor)
        }
    }

    private fun assertProviderValidationFails(
        configure: OidcProviderConfig<OidcToken>.() -> Unit,
    ): Throwable = assertFailsWith<IllegalArgumentException> {
        testApplication {
            openIdProvider()
            val openIdClient = openIdHttpClient()
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
