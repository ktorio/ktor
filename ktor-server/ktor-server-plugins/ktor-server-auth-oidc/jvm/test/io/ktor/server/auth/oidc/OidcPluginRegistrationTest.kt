/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.*

class OidcPluginRegistrationTest {

    @Test
    fun `plugin and provider helpers expose expected state`() = testApplication {
        val providers = mutableListOf<JwkProvider>()
        val provider = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = {
                        // don't convert to lambda because compiler would reuse the same instance every time
                        object : JwkProvider {
                            override fun get(keyId: String?): Jwk? {
                                error("JWK lookup is not used in this test")
                            }
                        }.also(providers::add)
                    }
                }
            },
        )

        provider.updateMetadata(openIdProviderMetadata)
        val initialJwkProvider = provider.currentJwkProvider()

        val updatedMetadata = OpenIdProviderMetadata(
            issuer = ISSUER_URL,
            authorizationEndpoint = "$ISSUER_URL/authorize-updated",
            tokenEndpoint = "$ISSUER_URL/token",
            jwksUri = "$ISSUER_URL/jwks",
        )
        provider.updateMetadata(updatedMetadata)
        assertEquals(updatedMetadata, provider.currentMetadata())
        assertSame(initialJwkProvider, provider.currentJwkProvider())
        assertEquals(1, providers.size)

        provider.updateMetadata(
            OpenIdProviderMetadata(
                issuer = ISSUER_URL,
                authorizationEndpoint = "$ISSUER_URL/authorize-updated",
                tokenEndpoint = "$ISSUER_URL/token",
                jwksUri = "$ISSUER_URL/jwks-updated",
            )
        )
        assertNotSame(initialJwkProvider, provider.currentJwkProvider())
        assertEquals(2, providers.size)

        val providerWithoutSchemes = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply {
                issuer = ISSUER_URL
            },
        )
        assertFailsWith<IllegalStateException> { providerWithoutSchemes.jwtBearer }
        assertFailsWith<IllegalStateException> { providerWithoutSchemes.introspectionBearer }
        assertFailsWith<IllegalStateException> { providerWithoutSchemes.session }

        val providerWithoutIntrospection = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply {
                issuer = ISSUER_URL
                bearer { audience = setOf("api") }
            },
        )
        assertFailsWith<IllegalStateException> { providerWithoutIntrospection.introspectionBearer }

        startApplication()
    }

    @Test
    fun `concurrent provider registration is synchronized`() {
        assertConcurrentDuplicateRegistrations(
            providerNames = List(16) { "auth0" },
            expectedFailureMessage = "already configured",
        ) {
            bearer { audience = setOf("api") }
        }

        assertConcurrentDuplicateRegistrations(
            providerNames = List(16) { index -> "auth0-$index" },
            expectedFailureMessage = "Duplicate OIDC issuer",
        )

        assertConcurrentDistinctRegistrations()
    }

    @Test
    fun `provider registration validates names and duplicate typed providers`() {
        val invalidNames = listOf("Google", "google_auth", "-google", "google-", "google--auth")
        invalidNames.forEach { providerName ->
            val failure = assertFailsWith<IllegalArgumentException> {
                testApplication {
                    application {
                        val oidc = install(Oidc)
                        oidc.identityProvider(providerName) {
                            testIssuer()
                        }
                    }
                    startApplication()
                }
            }
            assertContains(failure.message.orEmpty(), "provider name")
        }

        testApplication {
            application {
                val oidc = install(Oidc)
                oidc.identityProvider("auth0") {
                    testIssuer()
                }

                val failure = assertFailsWith<IllegalArgumentException> {
                    oidc.identityProvider("auth0") {
                        testIssuer()
                    }
                }
                assertContains(failure.message.orEmpty(), "already configured")
            }
        }
    }

    @Test
    fun `typed route registration rejects derived scheme name collisions`() {
        val secondIssuer = "https://okta.example.com"
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    val oidc = install(Oidc)
                    val auth0 = oidc.identityProvider("auth0") {
                        testIssuer()
                        bearer { audience = setOf("api") }
                    }
                    val okta = oidc.identityProvider("okta") {
                        testIssuer(secondIssuer)
                        oauth {
                            clientId = "client-id"
                            clientSecret = "client-secret"
                            sessions {
                                name = "auth0-jwt-bearer"
                            }
                        }
                    }

                    routing {
                        authenticateWith(auth0.jwtBearer) {
                            get("/auth0") {
                                call.respondText("auth0")
                            }
                        }
                        authenticateWith(okta.session) {
                            get("/okta") {
                                call.respondText("okta")
                            }
                        }
                    }
                }
                startApplication()
            }
        }

        assertContains(failure.message.orEmpty(), "auth0-jwt-bearer")
        assertContains(failure.message.orEmpty(), "already registered")
    }

    private fun assertConcurrentDuplicateRegistrations(
        providerNames: List<String>,
        expectedFailureMessage: String,
        configureProvider: OidcProviderConfig.() -> Unit = {},
    ) = testApplication {
        application {
            val oidc = install(Oidc)

            val results = coroutineScope {
                providerNames.map { providerName ->
                    async {
                        runCatching {
                            oidc.identityProvider(providerName) {
                                testIssuer()
                                configureProvider()
                            }
                        }
                    }
                }.awaitAll()
            }

            assertEquals(1, results.count { it.isSuccess })
            val failures = results.mapNotNull { it.exceptionOrNull() }
            assertEquals(providerNames.size - 1, failures.size)
            failures.forEach { failure ->
                assertIs<IllegalArgumentException>(failure)
                assertContains(failure.message.orEmpty(), expectedFailureMessage)
            }
        }

        startApplication()
    }

    private fun assertConcurrentDistinctRegistrations() = testApplication {
        val issuers = listOf(
            "auth0" to ISSUER_URL,
            "okta" to "https://okta.example.com",
            "keycloak" to "https://keycloak.example.com",
        )

        application {
            val oidc = install(Oidc)

            val providers = coroutineScope {
                issuers.map { (name, issuer) ->
                    async {
                        oidc.identityProvider(name) {
                            testIssuer(issuer)
                            bearer {
                                audience = setOf("api")
                            }
                        }
                    }
                }.awaitAll()
            }

            assertEquals(issuers.map { it.first }.toSet(), providers.map { it.name }.toSet())
        }

        startApplication()
    }
}
