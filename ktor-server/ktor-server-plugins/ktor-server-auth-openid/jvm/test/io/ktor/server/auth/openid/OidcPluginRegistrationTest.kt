/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcPluginRegistrationTest {

    @Test
    fun `plugin and provider helpers expose expected state`() = testApplication {
        val provider = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply {
                issuer = ISSUER_URL
            },
        )

        provider.updateMetadata(openIdProviderMetadata)
        assertEquals(openIdProviderMetadata, provider.currentMetadata())

        val updatedMetadata = OpenIdProviderMetadata(
            issuer = ISSUER_URL,
            authorizationEndpoint = "$ISSUER_URL/authorize-updated",
            tokenEndpoint = "$ISSUER_URL/token",
            jwksUri = "$ISSUER_URL/jwks",
        )
        provider.updateMetadata(updatedMetadata)
        assertEquals(updatedMetadata, provider.currentMetadata())

        application {
            val installed: Oidc = openIdConnect { }
            assertSame(installed, openIdConnect())
        }
        startApplication()
    }

    @Test
    fun `concurrent provider registration is synchronized`() {
        assertConcurrentDuplicateRegistrations(
            providerNames = List(16) { "auth0" },
            expectedFailureMessage = "already configured",
        )

        assertConcurrentDuplicateRegistrations(
            providerNames = List(16) { index -> "auth0-$index" },
            expectedFailureMessage = "Duplicate OIDC issuer",
        )

        assertConcurrentDistinctRegistrations()
    }

    @Test
    fun `provider registration validates names and duplicate providers`() {
        val invalidNames = listOf("Google", "google_auth", "-google", "google-", "google--auth")
        invalidNames.forEach { providerName ->
            val failure = assertFailsWith<IllegalArgumentException> {
                testApplication {
                    val openIdClient = client
                    application {
                        val oidc = openIdConnect {
                            httpClient = openIdClient
                            discoveryRefreshInterval = ZERO
                        }
                        oidc.provider(providerName) {
                            issuer = ISSUER_URL
                        }
                    }
                    startApplication()
                }
            }
            assertContains(failure.message.orEmpty(), "provider name")
        }

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
                }

                val failure = assertFailsWith<IllegalArgumentException> {
                    oidc.provider("auth0") {
                        issuer = ISSUER_URL
                    }
                }
                assertContains(failure.message.orEmpty(), "already configured")
            }
        }
    }

    private fun assertConcurrentDuplicateRegistrations(
        providerNames: List<String>,
        expectedFailureMessage: String,
    ) = testApplication {
        val discoveryRequests = AtomicInteger()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respondText(
                            openIdTestJson.encodeToString(openIdProviderMetadata),
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }

            val results = coroutineScope {
                providerNames.map { providerName ->
                    async {
                        runCatching {
                            oidc.provider(providerName) {
                                issuer = ISSUER_URL
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
        assertEquals(1, discoveryRequests.get())
    }

    private fun assertConcurrentDistinctRegistrations() = testApplication {
        val issuers = listOf(
            "auth0" to ISSUER_URL,
            "okta" to "https://okta.example.com",
            "keycloak" to "https://keycloak.example.com",
        )
        issuers.forEach { (_, issuer) -> openIdProvider(issuer) }

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }

            val providers = coroutineScope {
                issuers.map { (name, issuer) ->
                    async {
                        oidc.provider(name) {
                            this.issuer = issuer
                        }
                    }
                }.awaitAll()
            }

            assertEquals(issuers.map { it.first }.toSet(), providers.map { it.name }.toSet())
        }

        startApplication()
    }
}
