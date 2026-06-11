/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcEnvironmentConfigTest {

    @Test
    fun `environment provider is ignored until registered in code`() = testApplication {
        val discoveryRequests = AtomicInteger()

        environment {
            config = oidcEnvironmentConfig()
        }
        externalServices {
            hosts(ISSUER_URL) {
                installDiscoveryContentNegotiation()
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respond(openIdProviderMetadata)
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        application {
            openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
        }

        val login = noRedirectsClient().get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.NotFound, login.status)
        assertEquals(0, discoveryRequests.get())
    }

    @Test
    fun `environment provider can be extended in code`() = testApplication {
        environment {
            config = oidcEnvironmentConfig(withScopes = true)
        }

        application {
            val oidc = openIdConnect { }
            oidc.provider("auth0") {
                metadata = testOpenIdProviderMetadata(issuer)
                oauth {
                    loginUri = { path("custom", "login") }
                }
            }
        }

        val login = noRedirectsClient().get("/custom/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        assertEquals("/authorize", authorizeUrl.encodedPath)
        assertEquals("client-id", authorizeUrl.parameters["client_id"])
        assertEquals("openid profile", authorizeUrl.parameters["scope"])
    }

    @Test
    fun `failed provider configuration does not consume environment provider config`() = testApplication {
        environment {
            config = oidcEnvironmentConfig(withScopes = true)
        }

        application {
            val oidc = openIdConnect { }

            val failure = assertFailsWith<IllegalArgumentException> {
                oidc.provider("auth0") {
                    oauth {
                        scopes = emptyList()
                    }
                }
            }
            assertContains(failure.message.orEmpty(), "must include openid")

            oidc.provider("auth0") {
                metadata = testOpenIdProviderMetadata(issuer)
                oauth {
                    loginUri = { path("retry", "login") }
                }
            }
        }

        val login = noRedirectsClient().get("/retry/login")
        assertEquals(HttpStatusCode.Found, login.status)
        assertEquals("openid profile", Url(assertNotNull(login.headers[HttpHeaders.Location])).parameters["scope"])
    }

    @Test
    fun `environment provider rejects partial oauth credentials`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "ktor.oidc.provider.issuer" to ISSUER_URL,
                        "ktor.oidc.provider.clientId" to "client-id",
                    )
                }

                application {
                    openIdConnect {
                        discoveryRefreshInterval = ZERO
                    }
                }
                startApplication()
            }
        }

        assertContains(failure.message.orEmpty(), "clientId and clientSecret")
    }

    private fun oidcEnvironmentConfig(
        providerName: String = "auth0",
        withScopes: Boolean = false,
    ): MapApplicationConfig {
        val values = mutableMapOf(
            "ktor.oidc.$providerName.issuer" to ISSUER_URL,
            "ktor.oidc.$providerName.clientId" to "client-id",
            "ktor.oidc.$providerName.clientSecret" to "client-secret",
        )
        if (withScopes) {
            values["ktor.oidc.$providerName.scopes.0"] = "openid"
            values["ktor.oidc.$providerName.scopes.1"] = "profile"
            values["ktor.oidc.$providerName.scopes.size"] = "2"
        }
        return MapApplicationConfig(*values.entries.map { it.key to it.value }.toTypedArray())
    }
}
