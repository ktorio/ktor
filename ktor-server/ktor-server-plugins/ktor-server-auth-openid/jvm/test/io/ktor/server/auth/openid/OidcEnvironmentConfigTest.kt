/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.openid.utils.*
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
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respondText(
                            openIdTestJson.encodeToString(openIdProviderMetadata),
                            ContentType.Application.Json
                        )
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
        openIdProvider()

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                oauth {
                    loginUri { path("custom", "login") }
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
    fun `typed environment provider can be extended in code`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        environment {
            config = oidcEnvironmentConfig(providerName = "google", withScopes = true)
        }
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    openIdDiscoveryEndpoint()
                    post("/token") {
                        respondAuthorizationCodeWithIdToken(
                            parameters = call.receiveParameters(),
                            idTokensByState = idTokensByState,
                            accessToken = "access-token",
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
            oidc.provider<UserIdPrincipal>(
                name = "google",
                transformPrincipal = { principal ->
                    val idToken = principal as? OidcPrincipal.IdToken
                    idToken?.userInfo?.subject?.let(::UserIdPrincipal)
                }
            ) {
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                oauth {
                    loginUri { path("google", "login") }
                    onSuccess { principal ->
                        call.respondText(principal.name)
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin("google") {
            url { path("google", "login") }
        }
        assertEquals("/authorize", login.authorizeUrl.encodedPath)
        assertEquals("client-id", login.authorizeUrl.parameters["client_id"])
        assertEquals("openid profile", login.authorizeUrl.parameters["scope"])
        idTokensByState[login.state] = keys.token(
            audience = "client-id",
            subject = "env-typed-user",
            nonce = login.nonce,
        )

        val callback = browser.completeOidcCallback(login, providerName = "google")
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("env-typed-user", callback.bodyAsText())
    }

    @Test
    fun `failed provider configuration does not consume environment provider config`() = testApplication {
        val discoveryRequests = AtomicInteger()

        environment {
            config = oidcEnvironmentConfig(withScopes = true)
        }
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respondText(
                            openIdTestJson.encodeToString(openIdProviderMetadata),
                            ContentType.Application.Json
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

            val failure = assertFailsWith<IllegalArgumentException> {
                oidc.provider("auth0") {
                    oauth {
                        scopes = emptyList()
                    }
                }
            }
            assertContains(failure.message.orEmpty(), "must include openid")

            oidc.provider("auth0") {
                oauth {
                    loginUri { path("retry", "login") }
                }
            }
        }

        val login = noRedirectsClient().get("/retry/login")
        assertEquals(HttpStatusCode.Found, login.status)
        assertEquals("openid profile", Url(assertNotNull(login.headers[HttpHeaders.Location])).parameters["scope"])
        assertEquals(1, discoveryRequests.get())
    }

    @Test
    fun `environment provider rejects partial oauth credentials`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "ktor.openid.oidc.issuer" to ISSUER_URL,
                        "ktor.openid.oidc.clientId" to "client-id",
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
            "ktor.openid.$providerName.issuer" to ISSUER_URL,
            "ktor.openid.$providerName.clientId" to "client-id",
            "ktor.openid.$providerName.clientSecret" to "client-secret",
        )
        if (withScopes) {
            values["ktor.openid.$providerName.scopes.0"] = "openid"
            values["ktor.openid.$providerName.scopes.1"] = "profile"
            values["ktor.openid.$providerName.scopes.size"] = "2"
        }
        return MapApplicationConfig(*values.entries.map { it.key to it.value }.toTypedArray())
    }
}
